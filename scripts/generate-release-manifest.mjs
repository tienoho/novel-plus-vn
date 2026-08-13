#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { isDeepStrictEqual } from "node:util";

export const RELEASE_IMAGES = Object.freeze([
  "novel-front",
  "novel-admin",
  "novel-crawl",
  "novel-migrations",
  "novel-caddy",
  "novel-backup",
  "novel-alertmanager",
  "novel-pushgateway",
  "novel-mysql",
  "novel-grafana",
]);

export const RUNTIME_DEPENDENCIES = Object.freeze([
  Object.freeze({
    name: "redis",
    ref: "docker.io/library/redis:7-alpine@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2",
    artifactName: "docker.io/library/redis@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2",
  }),
  Object.freeze({
    name: "prometheus",
    ref: "quay.io/prometheus/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69",
    artifactName: "quay.io/prometheus/prometheus@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69",
  }),
]);

const COMPOSE_IMAGE_ENV_NAMES = Object.freeze({
  "novel-front": "NOVEL_FRONT_IMAGE",
  "novel-admin": "NOVEL_ADMIN_IMAGE",
  "novel-crawl": "NOVEL_CRAWL_IMAGE",
  "novel-migrations": "NOVEL_MIGRATIONS_IMAGE",
  "novel-caddy": "NOVEL_CADDY_IMAGE",
  "novel-backup": "NOVEL_BACKUP_IMAGE",
  "novel-alertmanager": "NOVEL_ALERTMANAGER_IMAGE",
  "novel-pushgateway": "NOVEL_PUSHGATEWAY_IMAGE",
  "novel-mysql": "NOVEL_MYSQL_IMAGE",
  "novel-grafana": "NOVEL_GRAFANA_IMAGE",
});

const SHA256_PATTERN = /^sha256:[0-9a-f]{64}$/;
const COMMIT_PATTERN = /^[0-9a-f]{40}$/;
const TAG_PATTERN = /^v[0-9][0-9A-Za-z._-]*$/;
const REPOSITORY_PATTERN = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;
const IMAGE_PREFIX_PATTERN = /^docker\.io\/[A-Za-z0-9_.-]+$/;

function invariant(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function readUtf8(file) {
  invariant(fs.existsSync(file), `Thiếu artifact bắt buộc: ${file}`);
  return fs.readFileSync(file, "utf8");
}

function sha256File(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function parseJson(file, label) {
  try {
    return JSON.parse(readUtf8(file).replace(/^\uFEFF/, ""));
  } catch (error) {
    throw new Error(`${label} không phải JSON hợp lệ: ${file}: ${error.message}`);
  }
}

function parseDigest(file) {
  const values = new Map();
  for (const line of readUtf8(file).split(/\r?\n/)) {
    if (!line.trim()) continue;
    const separator = line.indexOf("=");
    invariant(separator > 0, `Dòng digest không hợp lệ trong ${file}`);
    const key = line.slice(0, separator);
    const value = line.slice(separator + 1);
    invariant(!values.has(key), `Khóa digest bị trùng trong ${file}: ${key}`);
    values.set(key, value);
  }
  invariant(values.size === 2, `File digest phải chỉ có image_id và digest: ${file}`);
  invariant(SHA256_PATTERN.test(values.get("image_id") ?? ""), `image_id không hợp lệ: ${file}`);
  invariant(SHA256_PATTERN.test(values.get("digest") ?? ""), `registry digest không hợp lệ: ${file}`);
  return Object.fromEntries(values);
}

function validateSbom(file) {
  const sbom = parseJson(file, "SBOM");
  invariant(sbom.spdxVersion === "SPDX-2.3", `SBOM không phải SPDX 2.3: ${file}`);
  invariant(Array.isArray(sbom.packages) && sbom.packages.length > 0, `SBOM không có package: ${file}`);
  invariant(
    Array.isArray(sbom.relationships)
      && sbom.relationships.some((item) => item.relationshipType === "DESCRIBES"),
    `SBOM không có quan hệ DESCRIBES: ${file}`,
  );
  return {
    packageCount: sbom.packages.length,
    relationshipCount: sbom.relationships.length,
  };
}

function validateAttestation(file, image, imagePrefix, digest) {
  const data = parseJson(file, "Bằng chứng attestation");
  const subject = `${imagePrefix}/${image}`;
  invariant(data.image === image, `Attestation không khớp image ${image}: ${file}`);
  invariant(data.subject === subject, `Attestation không khớp subject ${subject}: ${file}`);
  invariant(data.digest === digest, `Attestation không khớp digest ${image}: ${file}`);
  for (const kind of ["provenance", "sbom"]) {
    invariant(data[kind] && typeof data[kind] === "object", `Thiếu attestation ${kind}: ${file}`);
    invariant(typeof data[kind].id === "string" && data[kind].id.trim(), `Thiếu ID ${kind}: ${file}`);
    invariant(
      typeof data[kind].url === "string" && /^https:\/\/github\.com\//.test(data[kind].url),
      `URL attestation ${kind} không hợp lệ: ${file}`,
    );
  }
  return data;
}

function validateMetadata(metadata) {
  invariant(TAG_PATTERN.test(metadata.tag ?? ""), "Tag release phải bắt đầu bằng v và theo sau bởi chữ số.");
  invariant(COMMIT_PATTERN.test(metadata.commit ?? ""), "Commit release phải là SHA 40 ký tự.");
  invariant(REPOSITORY_PATTERN.test(metadata.repository ?? ""), "Repository phải có dạng owner/repo.");
  invariant(IMAGE_PREFIX_PATTERN.test(metadata.imagePrefix ?? ""), "Image prefix phải có dạng docker.io/namespace.");
  invariant(/^\d+$/.test(String(metadata.runId ?? "")), "GitHub Actions run ID không hợp lệ.");
  invariant(
    metadata.runUrl === `https://github.com/${metadata.repository}/actions/runs/${metadata.runId}`,
    "GitHub Actions run URL không khớp repository/run ID.",
  );
  invariant(!Number.isNaN(Date.parse(metadata.generatedAt)), "Thời điểm sinh manifest không hợp lệ.");
}

function artifactNames(image) {
  return {
    digest: `digest-${image}.txt`,
    trivy: `trivy-${image}.txt`,
    sbom: `sbom-release-${image}.spdx.json`,
    attestation: `attestation-${image}.json`,
  };
}

function buildManifest(artifactsDir, metadata) {
  validateMetadata(metadata);
  const expectedFiles = new Set(RELEASE_IMAGES.flatMap((image) => Object.values(artifactNames(image))));
  const actualFiles = fs.readdirSync(artifactsDir, { withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => entry.name)
    .sort();
  const unexpected = actualFiles.filter((file) => !expectedFiles.has(file));
  invariant(unexpected.length === 0, `Artifact release không được nhận diện: ${unexpected.join(", ")}`);
  invariant(actualFiles.length === expectedFiles.size, "Bộ artifact release thiếu hoặc bị trùng file.");

  const images = RELEASE_IMAGES.map((image) => {
    const names = artifactNames(image);
    const files = Object.fromEntries(
      Object.entries(names).map(([kind, name]) => [kind, path.join(artifactsDir, name)]),
    );
    const digest = parseDigest(files.digest);
    const trivySize = fs.statSync(files.trivy).size;
    invariant(trivySize > 0, `Report Trivy rỗng: ${files.trivy}`);
    const sbom = validateSbom(files.sbom);
    const attestation = validateAttestation(
      files.attestation,
      image,
      metadata.imagePrefix,
      digest.digest,
    );
    return {
      name: image,
      subject: `${metadata.imagePrefix}/${image}`,
      imageId: digest.image_id,
      digest: digest.digest,
      artifacts: Object.fromEntries(
        Object.entries(names).map(([kind, name]) => [kind, { path: name, sha256: sha256File(files[kind]) }]),
      ),
      sbom: {
        packageCount: sbom.packageCount,
        relationshipCount: sbom.relationshipCount,
      },
      attestations: {
        provenance: attestation.provenance,
        sbom: attestation.sbom,
      },
    };
  });

  return {
    schemaVersion: 1,
    release: {
      tag: metadata.tag,
      commit: metadata.commit,
      repository: metadata.repository,
      imagePrefix: metadata.imagePrefix,
      workflowRun: {
        id: String(metadata.runId),
        url: metadata.runUrl,
      },
      generatedAt: metadata.generatedAt,
    },
    images,
  };
}

export function createReleaseManifest(options) {
  const artifactsDir = path.resolve(options.artifactsDir);
  invariant(fs.existsSync(artifactsDir) && fs.statSync(artifactsDir).isDirectory(), "Thiếu thư mục artifact release.");
  const output = path.resolve(options.output);
  const checksum = path.resolve(options.checksum);
  const manifest = buildManifest(artifactsDir, {
    tag: options.tag,
    commit: options.commit,
    repository: options.repository,
    imagePrefix: options.imagePrefix,
    runId: options.runId,
    runUrl: options.runUrl,
    generatedAt: options.generatedAt ?? new Date().toISOString(),
  });
  fs.writeFileSync(output, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  fs.writeFileSync(checksum, `${sha256File(output)}  ${path.basename(output)}\n`, "utf8");
  return manifest;
}

export function verifyReleaseManifest(options) {
  const manifestPath = path.resolve(options.manifest);
  const checksumPath = path.resolve(options.checksum);
  const artifactsDir = path.resolve(options.artifactsDir);
  const manifest = parseJson(manifestPath, "Release manifest");
  invariant(manifest.schemaVersion === 1, "Release manifest có schemaVersion không được hỗ trợ.");
  invariant(manifest.release?.tag === options.tag, "Release manifest không khớp tag hiện tại.");
  invariant(manifest.release?.commit === options.commit, "Release manifest không khớp commit hiện tại.");

  const checksumLine = readUtf8(checksumPath).trim();
  const expectedChecksum = `${sha256File(manifestPath)}  ${path.basename(manifestPath)}`;
  invariant(checksumLine === expectedChecksum, "Checksum release manifest không hợp lệ.");

  const rebuilt = buildManifest(artifactsDir, {
    tag: manifest.release.tag,
    commit: manifest.release.commit,
    repository: manifest.release.repository,
    imagePrefix: manifest.release.imagePrefix,
    runId: manifest.release.workflowRun?.id,
    runUrl: manifest.release.workflowRun?.url,
    generatedAt: manifest.release.generatedAt,
  });
  invariant(isDeepStrictEqual(manifest, rebuilt), "Release manifest không khớp bộ artifact hiện tại.");
  return manifest;
}

function createComposeImageEnv(manifest, manifestPath, output) {
  const manifestHash = sha256File(manifestPath);
  const lines = manifest.images.map((image) => {
    const name = COMPOSE_IMAGE_ENV_NAMES[image.name];
    invariant(name, `Image không hỗ trợ triển khai Compose: ${image.name}`);
    return `${name}=${image.subject}@${image.digest}`;
  });
  invariant(lines.length === RELEASE_IMAGES.length, "Compose env không có đủ image release.");
  lines.push(
    `NOVEL_RELEASE_TAG=${manifest.release.tag}`,
    `NOVEL_RELEASE_COMMIT=${manifest.release.commit}`,
    `NOVEL_RELEASE_MANIFEST_SHA256=${manifestHash}`,
  );
  fs.writeFileSync(path.resolve(output), `${lines.join("\n")}\n`, "utf8");
  return { lines, manifestHash };
}

export function createCandidateEnv(options) {
  const manifestPath = path.resolve(options.manifest);
  const manifest = verifyReleaseManifest({
    artifactsDir: options.artifactsDir,
    manifest: manifestPath,
    checksum: options.checksum,
    tag: options.tag,
    commit: options.commit,
  });
  const env = createComposeImageEnv(manifest, manifestPath, options.output);
  return { manifest, ...env };
}

export function verifyCandidateSmokeRecord(options) {
  const manifestPath = path.resolve(options.manifest);
  const manifest = parseJson(manifestPath, "Release manifest");
  const record = parseJson(path.resolve(options.record), "Biên bản smoke RC");
  invariant(manifest.release?.tag === options.tag, "Release manifest không khớp tag smoke RC.");
  invariant(manifest.release?.commit === options.commit, "Release manifest không khớp commit smoke RC.");
  invariant(record.schemaVersion === 1, "Biên bản smoke RC có schemaVersion không được hỗ trợ.");
  invariant(record.status === "PASSED", "Biên bản smoke RC không có trạng thái PASSED.");
  invariant(record.tag === options.tag, "Biên bản smoke RC không khớp tag.");
  invariant(record.commit === options.commit, "Biên bản smoke RC không khớp commit.");
  invariant(record.imageCount === RELEASE_IMAGES.length, "Biên bản smoke RC không có đúng mười image.");
  invariant(record.backupRestoreVerified === true, "Biên bản smoke RC chưa xác minh backup và restore drill.");
  invariant(
    record.releaseManifestSha256 === sha256File(manifestPath),
    "Biên bản smoke RC không khớp hash release manifest.",
  );
  invariant(
    record.workflowRun === manifest.release.workflowRun?.url,
    "Biên bản smoke RC không khớp workflow run phát hành.",
  );
  invariant(!Number.isNaN(Date.parse(record.verifiedAt)), "Biên bản smoke RC thiếu thời điểm xác minh hợp lệ.");
  return { manifest, record };
}

export function verifyRuntimeDependencyReports(options) {
  const reportsDir = path.resolve(options.reportsDir);
  invariant(fs.existsSync(reportsDir) && fs.statSync(reportsDir).isDirectory(), "Thiếu thư mục report dependency runtime.");
  const expectedFiles = new Set(RUNTIME_DEPENDENCIES.map(({ name }) => `trivy-runtime-${name}.json`));
  const actualFiles = fs.readdirSync(reportsDir, { withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => entry.name)
    .sort();
  invariant(
    actualFiles.length === expectedFiles.size && actualFiles.every((file) => expectedFiles.has(file)),
    "Bộ report dependency runtime thiếu, thừa hoặc sai tên file.",
  );
  const reports = RUNTIME_DEPENDENCIES.map((dependency) => {
    const file = path.join(reportsDir, `trivy-runtime-${dependency.name}.json`);
    const report = parseJson(file, `Report Trivy runtime ${dependency.name}`);
    invariant(report.SchemaVersion === 2, `Report Trivy runtime sai schema: ${dependency.name}`);
    invariant(report.ArtifactType === "container_image", `Report Trivy runtime sai artifact type: ${dependency.name}`);
    invariant(report.ArtifactName === dependency.artifactName, `Report Trivy runtime sai digest: ${dependency.name}`);
    for (const result of report.Results ?? []) {
      for (const field of ["Vulnerabilities", "Misconfigurations", "Secrets"]) {
        invariant(
          !Array.isArray(result[field]) || result[field].length === 0,
          `Dependency runtime còn finding ${field}: ${dependency.name}`,
        );
      }
    }
    return { ...dependency, sha256: sha256File(file) };
  });
  return reports;
}

export function createDeploymentEnv(options) {
  const promotion = parseJson(path.resolve(options.promotionRecord), "Promotion record");
  invariant(promotion.tag === options.tag, "Promotion record không khớp tag triển khai.");
  invariant(COMMIT_PATTERN.test(promotion.commit ?? ""), "Commit trong promotion record không hợp lệ.");
  invariant(
    typeof promotion.approvalRecordId === "string"
      && /^[A-Za-z0-9._:/-]{3,128}$/.test(promotion.approvalRecordId),
    "Mã biên bản trong promotion record không hợp lệ.",
  );
  invariant(/^[0-9a-f]{64}$/.test(promotion.approvalRecordSha256 ?? ""), "Hash biên bản phê duyệt không hợp lệ.");
  invariant(/^[0-9a-f]{64}$/.test(promotion.releaseManifestSha256 ?? ""), "Hash manifest trong promotion record không hợp lệ.");
  invariant(typeof promotion.promotedBy === "string" && promotion.promotedBy.trim(), "Promotion record thiếu người thực hiện.");
  invariant(
    typeof promotion.workflowRun === "string" && /^https:\/\/github\.com\//.test(promotion.workflowRun),
    "Promotion record thiếu workflow run hợp lệ.",
  );
  invariant(!Number.isNaN(Date.parse(promotion.promotedAt)), "Thời điểm promotion không hợp lệ.");

  const manifest = verifyReleaseManifest({
    artifactsDir: options.artifactsDir,
    manifest: options.manifest,
    checksum: options.checksum,
    tag: options.tag,
    commit: promotion.commit,
  });
  const manifestHash = sha256File(path.resolve(options.manifest));
  invariant(promotion.releaseManifestSha256 === manifestHash, "Promotion record không khớp hash release manifest.");
  invariant(promotion.commit === manifest.release.commit, "Promotion record không khớp commit release manifest.");

  const env = createComposeImageEnv(manifest, path.resolve(options.manifest), options.output);
  invariant(env.manifestHash === manifestHash, "Hash Compose env không khớp release manifest.");
  return { manifest, promotion, lines: env.lines };
}

function parseArgs(args) {
  const values = {};
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index];
    invariant(key?.startsWith("--") && index + 1 < args.length, `Tham số CLI không hợp lệ: ${key ?? ""}`);
    values[key.slice(2)] = args[index + 1];
  }
  return values;
}

function required(values, key) {
  invariant(typeof values[key] === "string" && values[key].trim(), `Thiếu tham số --${key}`);
  return values[key];
}

function runCli() {
  const [command, ...args] = process.argv.slice(2);
  const values = parseArgs(args);
  if (command === "create") {
    const manifest = createReleaseManifest({
      artifactsDir: required(values, "artifacts"),
      output: required(values, "output"),
      checksum: required(values, "checksum"),
      tag: required(values, "tag"),
      commit: required(values, "commit"),
      repository: required(values, "repository"),
      imagePrefix: required(values, "image-prefix"),
      runId: required(values, "run-id"),
      runUrl: required(values, "run-url"),
    });
    console.log(`Đã tạo release manifest cho ${manifest.images.length} image.`);
    return;
  }
  if (command === "verify") {
    const manifest = verifyReleaseManifest({
      artifactsDir: required(values, "artifacts"),
      manifest: required(values, "manifest"),
      checksum: required(values, "checksum"),
      tag: required(values, "tag"),
      commit: required(values, "commit"),
    });
    console.log(`Release manifest hợp lệ: ${manifest.release.tag} / ${manifest.release.commit}.`);
    return;
  }
  if (command === "deploy-env") {
    const result = createDeploymentEnv({
      artifactsDir: required(values, "artifacts"),
      manifest: required(values, "manifest"),
      checksum: required(values, "checksum"),
      promotionRecord: required(values, "promotion-record"),
      output: required(values, "output"),
      tag: required(values, "tag"),
    });
    console.log(`Đã khóa Compose theo digest của ${result.manifest.images.length} image.`);
    return;
  }
  if (command === "candidate-env") {
    const result = createCandidateEnv({
      artifactsDir: required(values, "artifacts"),
      manifest: required(values, "manifest"),
      checksum: required(values, "checksum"),
      output: required(values, "output"),
      tag: required(values, "tag"),
      commit: required(values, "commit"),
    });
    console.log(`Đã khóa smoke RC theo digest của ${result.manifest.images.length} image.`);
    return;
  }
  if (command === "verify-smoke-record") {
    const result = verifyCandidateSmokeRecord({
      manifest: required(values, "manifest"),
      record: required(values, "record"),
      tag: required(values, "tag"),
      commit: required(values, "commit"),
    });
    console.log(`Biên bản smoke RC hợp lệ: ${result.record.tag} / ${result.record.commit}.`);
    return;
  }
  if (command === "verify-runtime-reports") {
    const reports = verifyRuntimeDependencyReports({
      reportsDir: required(values, "reports"),
    });
    console.log(`Bộ report dependency runtime hợp lệ: ${reports.length} image.`);
    return;
  }
  throw new Error("Lệnh phải là create, verify, candidate-env, verify-smoke-record, verify-runtime-reports hoặc deploy-env.");
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  try {
    runCli();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
