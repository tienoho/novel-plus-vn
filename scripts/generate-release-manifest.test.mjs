import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  RELEASE_IMAGES,
  RUNTIME_DEPENDENCIES,
  createCandidateEnv,
  createDeploymentEnv,
  createReleaseManifest,
  verifyCandidateSmokeRecord,
  verifyReleaseManifest,
  verifyRuntimeDependencyReports,
} from "./generate-release-manifest.mjs";

const metadata = Object.freeze({
  tag: "v1.2.3",
  commit: "a".repeat(40),
  repository: "novel-plus/novel-plus",
  imagePrefix: "docker.io/novelplus",
  runId: "123456",
  runUrl: "https://github.com/novel-plus/novel-plus/actions/runs/123456",
  generatedAt: "2026-08-09T00:00:00.000Z",
});
const cli = fileURLToPath(new URL("./generate-release-manifest.mjs", import.meta.url));

function sha(value) {
  return `sha256:${crypto.createHash("sha256").update(value).digest("hex")}`;
}

function createFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "novel-plus-release-manifest-"));
  const artifactsDir = path.join(root, "artifacts");
  fs.mkdirSync(artifactsDir);
  for (const [index, image] of RELEASE_IMAGES.entries()) {
    const digest = sha(`registry-${image}`);
    fs.writeFileSync(
      path.join(artifactsDir, `digest-${image}.txt`),
      `image_id=${sha(`local-${image}`)}\ndigest=${digest}\n`,
    );
    fs.writeFileSync(path.join(artifactsDir, `trivy-${image}.txt`), "HIGH: 0, CRITICAL: 0\n");
    fs.writeFileSync(
      path.join(artifactsDir, `sbom-release-${image}.spdx.json`),
      `${index === 0 ? "\uFEFF" : ""}${JSON.stringify({
        spdxVersion: "SPDX-2.3",
        packages: [{ SPDXID: `SPDXRef-${index}` }],
        relationships: [{
          spdxElementId: "SPDXRef-DOCUMENT",
          relationshipType: "DESCRIBES",
          relatedSpdxElement: `SPDXRef-${index}`,
        }],
      })}`,
    );
    fs.writeFileSync(
      path.join(artifactsDir, `attestation-${image}.json`),
      JSON.stringify({
        image,
        subject: `${metadata.imagePrefix}/${image}`,
        digest,
        provenance: { id: `provenance-${index}`, url: `https://github.com/attestations/p-${index}` },
        sbom: { id: `sbom-${index}`, url: `https://github.com/attestations/s-${index}` },
      }),
    );
  }
  return {
    root,
    artifactsDir,
    manifest: path.join(root, "release-manifest.json"),
    checksum: path.join(root, "release-manifest.sha256"),
  };
}

function withFixture(callback) {
  const fixture = createFixture();
  try {
    callback(fixture);
  } finally {
    fs.rmSync(fixture.root, { recursive: true, force: true });
  }
}

function createPromotionRecord(fixture, overrides = {}) {
  const record = {
    tag: metadata.tag,
    commit: metadata.commit,
    approvalRecordId: "APPROVAL-2026-001",
    approvalRecordSha256: "b".repeat(64),
    releaseManifestSha256: crypto.createHash("sha256").update(fs.readFileSync(fixture.manifest)).digest("hex"),
    promotedBy: "release-manager",
    workflowRun: metadata.runUrl,
    promotedAt: metadata.generatedAt,
    ...overrides,
  };
  const file = path.join(fixture.root, "production-promotion-record.json");
  fs.writeFileSync(file, JSON.stringify(record));
  return file;
}

function createSmokeRecord(fixture, overrides = {}) {
  const record = {
    schemaVersion: 1,
    status: "PASSED",
    tag: metadata.tag,
    commit: metadata.commit,
    imageCount: RELEASE_IMAGES.length,
    backupRestoreVerified: true,
    releaseManifestSha256: crypto.createHash("sha256").update(fs.readFileSync(fixture.manifest)).digest("hex"),
    workflowRun: metadata.runUrl,
    verifiedAt: metadata.generatedAt,
    ...overrides,
  };
  const file = path.join(fixture.root, "rc-compose-smoke.json");
  fs.writeFileSync(file, JSON.stringify(record));
  return file;
}

function createRuntimeReports(fixture, findingName = "") {
  const reportsDir = path.join(fixture.root, "runtime-reports");
  fs.mkdirSync(reportsDir);
  for (const dependency of RUNTIME_DEPENDENCIES) {
    const vulnerabilities = dependency.name === findingName
      ? [{ VulnerabilityID: "CVE-2099-0001", Severity: "HIGH" }]
      : null;
    fs.writeFileSync(
      path.join(reportsDir, `trivy-runtime-${dependency.name}.json`),
      JSON.stringify({
        SchemaVersion: 2,
        ArtifactName: dependency.artifactName,
        ArtifactType: "container_image",
        Results: [{ Target: dependency.name, Vulnerabilities: vulnerabilities }],
      }),
    );
  }
  return reportsDir;
}

test("tạo và xác minh manifest gắn đúng mười image với RC", () => {
  withFixture((fixture) => {
    const created = createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    assert.deepEqual(created.images.map((image) => image.name), RELEASE_IMAGES);
    const verified = verifyReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      manifest: fixture.manifest,
      checksum: fixture.checksum,
      tag: metadata.tag,
      commit: metadata.commit,
    });
    assert.equal(verified.release.workflowRun.id, metadata.runId);
  });
});

test("CLI tạo rồi xác minh manifest theo đúng lệnh workflow", () => {
  withFixture((fixture) => {
    const create = spawnSync(process.execPath, [
      cli,
      "create",
      "--artifacts", fixture.artifactsDir,
      "--output", fixture.manifest,
      "--checksum", fixture.checksum,
      "--tag", metadata.tag,
      "--commit", metadata.commit,
      "--repository", metadata.repository,
      "--image-prefix", metadata.imagePrefix,
      "--run-id", metadata.runId,
      "--run-url", metadata.runUrl,
    ], { encoding: "utf8" });
    assert.equal(create.status, 0, create.stderr);

    const verify = spawnSync(process.execPath, [
      cli,
      "verify",
      "--artifacts", fixture.artifactsDir,
      "--manifest", fixture.manifest,
      "--checksum", fixture.checksum,
      "--tag", metadata.tag,
      "--commit", metadata.commit,
    ], { encoding: "utf8" });
    assert.equal(verify.status, 0, verify.stderr);
    assert.match(verify.stdout, /Release manifest hợp lệ/);
  });
});

test("sinh Compose env cho smoke RC từ manifest đã xác minh", () => {
  withFixture((fixture) => {
    createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    const output = path.join(fixture.root, ".env.rc-smoke");
    const result = createCandidateEnv({
      artifactsDir: fixture.artifactsDir,
      manifest: fixture.manifest,
      checksum: fixture.checksum,
      output,
      tag: metadata.tag,
      commit: metadata.commit,
    });
    assert.equal(result.lines.filter((line) => /_IMAGE=/.test(line)).length, 10);
    const env = fs.readFileSync(output, "utf8");
    assert.match(env, /^NOVEL_FRONT_IMAGE=docker\.io\/novelplus\/novel-front@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_ALERTMANAGER_IMAGE=docker\.io\/novelplus\/novel-alertmanager@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_PUSHGATEWAY_IMAGE=docker\.io\/novelplus\/novel-pushgateway@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_MYSQL_IMAGE=docker\.io\/novelplus\/novel-mysql@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_GRAFANA_IMAGE=docker\.io\/novelplus\/novel-grafana@sha256:[0-9a-f]{64}$/m);
    assert.match(env, new RegExp(`^NOVEL_RELEASE_COMMIT=${metadata.commit}$`, "m"));

    const cliOutput = path.join(fixture.root, ".env.rc-smoke.cli");
    const cliResult = spawnSync(process.execPath, [
      cli,
      "candidate-env",
      "--artifacts", fixture.artifactsDir,
      "--manifest", fixture.manifest,
      "--checksum", fixture.checksum,
      "--output", cliOutput,
      "--tag", metadata.tag,
      "--commit", metadata.commit,
    ], { encoding: "utf8" });
    assert.equal(cliResult.status, 0, cliResult.stderr);
    assert.equal(fs.readFileSync(cliOutput, "utf8"), env);
  });
});

test("từ chối candidate-env khi commit không khớp manifest", () => {
  withFixture((fixture) => {
    createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    assert.throws(() => createCandidateEnv({
      artifactsDir: fixture.artifactsDir,
      manifest: fixture.manifest,
      checksum: fixture.checksum,
      output: path.join(fixture.root, ".env.rc-smoke"),
      tag: metadata.tag,
      commit: "b".repeat(40),
    }), /không khớp commit hiện tại/);
  });
});

test("xác minh biên bản smoke RC khớp tag commit và manifest", () => {
  withFixture((fixture) => {
    createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    const record = createSmokeRecord(fixture);
    const verified = verifyCandidateSmokeRecord({
      manifest: fixture.manifest,
      record,
      tag: metadata.tag,
      commit: metadata.commit,
    });
    assert.equal(verified.record.status, "PASSED");

    const cliResult = spawnSync(process.execPath, [
      cli,
      "verify-smoke-record",
      "--manifest", fixture.manifest,
      "--record", record,
      "--tag", metadata.tag,
      "--commit", metadata.commit,
    ], { encoding: "utf8" });
    assert.equal(cliResult.status, 0, cliResult.stderr);
    assert.match(cliResult.stdout, /Biên bản smoke RC hợp lệ/);
  });
});

test("từ chối biên bản smoke RC không khớp hash manifest", () => {
  withFixture((fixture) => {
    createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    const record = createSmokeRecord(fixture, { releaseManifestSha256: "c".repeat(64) });
    assert.throws(() => verifyCandidateSmokeRecord({
      manifest: fixture.manifest,
      record,
      tag: metadata.tag,
      commit: metadata.commit,
    }), /không khớp hash release manifest/);
  });
});

test("từ chối biên bản smoke RC chưa xác minh backup restore", () => {
  withFixture((fixture) => {
    createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    const record = createSmokeRecord(fixture, { backupRestoreVerified: false });
    assert.throws(() => verifyCandidateSmokeRecord({
      manifest: fixture.manifest,
      record,
      tag: metadata.tag,
      commit: metadata.commit,
    }), /chưa xác minh backup và restore drill/);
  });
});

test("xác minh đủ hai report dependency runtime sạch theo digest", () => {
  withFixture((fixture) => {
    const reportsDir = createRuntimeReports(fixture);
    const reports = verifyRuntimeDependencyReports({ reportsDir });
    assert.equal(reports.length, 2);

    const cliResult = spawnSync(process.execPath, [
      cli,
      "verify-runtime-reports",
      "--reports", reportsDir,
    ], { encoding: "utf8" });
    assert.equal(cliResult.status, 0, cliResult.stderr);
    assert.match(cliResult.stdout, /2 image/);
  });
});

test("từ chối dependency runtime còn finding", () => {
  withFixture((fixture) => {
    const reportsDir = createRuntimeReports(fixture, "prometheus");
    assert.throws(
      () => verifyRuntimeDependencyReports({ reportsDir }),
      /Dependency runtime còn finding Vulnerabilities: prometheus/,
    );
  });
});

test("từ chối report dependency dùng ArtifactName không đúng dạng Trivy theo digest", () => {
  withFixture((fixture) => {
    const reportsDir = createRuntimeReports(fixture);
    const reportFile = path.join(reportsDir, "trivy-runtime-redis.json");
    const report = JSON.parse(fs.readFileSync(reportFile, "utf8"));
    report.ArtifactName = RUNTIME_DEPENDENCIES.find(({ name }) => name === "redis").ref;
    fs.writeFileSync(reportFile, JSON.stringify(report));
    assert.throws(
      () => verifyRuntimeDependencyReports({ reportsDir }),
      /Report Trivy runtime sai digest: redis/,
    );
  });
});

test("sinh Compose env chỉ từ manifest và promotion record khớp nhau", () => {
  withFixture((fixture) => {
    createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    const promotionRecord = createPromotionRecord(fixture);
    const output = path.join(fixture.root, ".env.release");
    const result = createDeploymentEnv({
      artifactsDir: fixture.artifactsDir,
      manifest: fixture.manifest,
      checksum: fixture.checksum,
      promotionRecord,
      output,
      tag: metadata.tag,
    });
    assert.equal(result.lines.filter((line) => /_IMAGE=/.test(line)).length, 10);
    const env = fs.readFileSync(output, "utf8");
    assert.match(env, /^NOVEL_FRONT_IMAGE=docker\.io\/novelplus\/novel-front@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_CADDY_IMAGE=docker\.io\/novelplus\/novel-caddy@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_BACKUP_IMAGE=docker\.io\/novelplus\/novel-backup@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_ALERTMANAGER_IMAGE=docker\.io\/novelplus\/novel-alertmanager@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_PUSHGATEWAY_IMAGE=docker\.io\/novelplus\/novel-pushgateway@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_MYSQL_IMAGE=docker\.io\/novelplus\/novel-mysql@sha256:[0-9a-f]{64}$/m);
    assert.match(env, /^NOVEL_GRAFANA_IMAGE=docker\.io\/novelplus\/novel-grafana@sha256:[0-9a-f]{64}$/m);
    assert.match(env, new RegExp(`^NOVEL_RELEASE_COMMIT=${metadata.commit}$`, "m"));

    const cliOutput = path.join(fixture.root, ".env.release.cli");
    const cliResult = spawnSync(process.execPath, [
      cli,
      "deploy-env",
      "--artifacts", fixture.artifactsDir,
      "--manifest", fixture.manifest,
      "--checksum", fixture.checksum,
      "--promotion-record", promotionRecord,
      "--output", cliOutput,
      "--tag", metadata.tag,
    ], { encoding: "utf8" });
    assert.equal(cliResult.status, 0, cliResult.stderr);
    assert.equal(fs.readFileSync(cliOutput, "utf8"), env);
  });
});

test("từ chối deploy-env khi promotion record không khớp manifest", () => {
  withFixture((fixture) => {
    createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    const promotionRecord = createPromotionRecord(fixture, { releaseManifestSha256: "c".repeat(64) });
    assert.throws(() => createDeploymentEnv({
      artifactsDir: fixture.artifactsDir,
      manifest: fixture.manifest,
      checksum: fixture.checksum,
      promotionRecord,
      output: path.join(fixture.root, ".env.release"),
      tag: metadata.tag,
    }), /không khớp hash release manifest/);
  });
});

test("từ chối artifact bị sửa sau khi tạo manifest", () => {
  withFixture((fixture) => {
    createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    });
    fs.appendFileSync(path.join(fixture.artifactsDir, "trivy-novel-front.txt"), "tampered\n");
    assert.throws(() => verifyReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      manifest: fixture.manifest,
      checksum: fixture.checksum,
      tag: metadata.tag,
      commit: metadata.commit,
    }), /không khớp bộ artifact/);
  });
});

test("từ chối bộ artifact thiếu một image", () => {
  withFixture((fixture) => {
    fs.rmSync(path.join(fixture.artifactsDir, "attestation-novel-crawl.json"));
    assert.throws(() => createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
    }), /thiếu hoặc bị trùng/);
  });
});

test("từ chối tag không phải release candidate", () => {
  withFixture((fixture) => {
    assert.throws(() => createReleaseManifest({
      artifactsDir: fixture.artifactsDir,
      output: fixture.manifest,
      checksum: fixture.checksum,
      ...metadata,
      tag: "main",
    }), /Tag release/);
  });
});
