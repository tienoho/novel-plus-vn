import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const HAN = /\p{Script=Han}/u;
const TEXT_EXTENSIONS = new Set([
    ".css", ".gitignore", ".html", ".java", ".js", ".json", ".less", ".md",
    ".properties", ".sh", ".sql", ".txt", ".vm", ".vue", ".xml", ".yaml", ".yml"
]);
const IGNORED_DIRECTORIES = new Set([".git", ".idea", "node_modules", "target"]);

const wholeFileAllowRules = [
    {
        test: (file) => file === "doc/i18n-han-allowlist.md",
        reason: "Tài liệu mô tả chính các token ngoại lệ."
    },
    {
        test: (file) => file.endsWith("messages_zh_CN.properties"),
        reason: "Bundle fallback tiếng Trung."
    },
    {
        test: (file) => /^doc\/sql\/.*\.sql$/u.test(file),
        reason: "Migration lịch sử, seed nội dung và giá trị nguồn cần so khớp chính xác."
    },
    {
        test: (file) => /\/(?:layui|lay|wangEditor|layuimini|plugins|fonts)\//iu.test("/" + file),
        reason: "Tài nguyên thư viện bên thứ ba."
    },
    {
        test: (file) => /novel-admin\/src\/main\/resources\/static\/(?:js\/layui\.js|css\/layui(?:\.mobile)?\.css|css\/style\.css)$/iu.test(file),
        reason: "Layui và theme H+ bên thứ ba."
    },
    {
        test: (file) => /\/(?:easyui-lang-zh_CN\.js|layer\.m\.js)$/iu.test("/" + file),
        reason: "Gói locale hoặc thư viện bên thứ ba được giữ nguyên."
    },
    {
        test: (file) => /templates\/dark\/static\/(?:mobile\/)?html\/note_[1-4]\.html$/u.test(file),
        reason: "Nội dung bài viết mẫu, không phải chuỗi giao diện."
    },
    {
        test: (file) => /templates\/(?:orange|dark)\/static\/(?:mobile\/)?(?:index|book_search|book_index|book_detail|book_content)\.html$/u.test(file),
        reason: "Tên truyện, tác giả, chương và nội dung truyện mẫu; phần giao diện đã được audit riêng."
    },
    {
        test: (file) => /\/src\/test\/java\/.*\/(?:I18nConfigTest|MessageCatalogTest)\.java$/u.test("/" + file),
        reason: "Dữ liệu kiểm thử fallback và danh sách token ngoại lệ."
    }
];

const exactTokenAllowlist = new Map();
function allowTokens(files, tokens) {
    for (const file of files) exactTokenAllowlist.set(file, tokens);
}

allowTokens([
    "novel-crawl/src/main/java/com/java2nb/novel/controller/CrawlController.java",
    "novel-crawl/src/main/resources/templates/crawl/crawlSource_test.html"
], ["是否匹配", "匹配结果"]);
allowTokens([
    "novel-crawl/src/main/java/com/java2nb/novel/core/crawl/CrawlParser.java"
], ["正在手打中"]);
allowTokens([
    "novel-crawl/src/main/resources/templates/crawl/crawlSource_add.html",
    "novel-crawl/src/main/resources/templates/crawl/crawlSource_update.html"
], ["作者", "状态", "连载", "完结", "分", "更新"]);

const regionTokens = [
    "北京市", "天津市", "上海市", "重庆市", "河北省", "山西省", "辽宁省", "吉林省",
    "黑龙江省", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省", "河南省",
    "湖北省", "湖南省", "广东省", "海南省", "四川省", "贵州省", "云南省", "陕西省",
    "甘肃省", "青海省", "台湾省", "内蒙古自治区", "广西壮族自治区", "西藏自治区",
    "宁夏回族自治区", "新疆维吾尔自治区", "香港", "香港特别行政区", "澳门", "澳门特别行政区", "中国"
];
allowTokens([
    "novel-front/src/main/java/com/java2nb/novel/service/impl/IpLocationServiceImpl.java"
], regionTokens);

allowTokens([
    "novel-front/src/main/resources/templates/author/book_add.html",
    "templates/green/html/author/book_add.html",
    "templates/orange/html/author/book_add.html"
], ["玄幻奇幻"]);

allowTokens([
    "novel-front/src/main/resources/templates/mobile/book/mh_book_search.html",
    "novel-front/src/main/resources/templates/mobile/book/soft_book_search.html",
    "templates/green/html/mobile/book/mh_book_search.html",
    "templates/green/html/mobile/book/soft_book_search.html",
    "templates/orange/html/mobile/book/mh_book_search.html",
    "templates/orange/html/mobile/book/soft_book_search.html",
    "templates/dark/html/mobile/book/mh_book_search.html",
    "templates/dark/html/mobile/book/soft_book_search.html"
], ["已完成"]);

function normalizePath(file) {
    return path.relative(ROOT, file).split(path.sep).join("/");
}

function walk(directory, files = []) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
        if (entry.isDirectory() && IGNORED_DIRECTORIES.has(entry.name)) continue;
        const absolute = path.join(directory, entry.name);
        if (entry.isDirectory()) walk(absolute, files);
        else files.push(absolute);
    }
    return files;
}

function isTextFile(file) {
    const basename = path.basename(file);
    return TEXT_EXTENSIONS.has(path.extname(file)) || basename === "Dockerfile";
}

function wholeFileAllowance(file) {
    return wholeFileAllowRules.find((rule) => rule.test(file));
}

function stripAllowedTokens(content, tokens) {
    let result = content;
    const longestFirst = [...(tokens || [])].sort((left, right) => right.length - left.length);
    for (const token of longestFirst) result = result.split(token).join("");
    return result;
}

function lineNumberAt(content, index) {
    return content.slice(0, index).split(/\r?\n/u).length;
}

function parseProperties(file) {
    const keys = new Map();
    const duplicates = [];
    const lines = fs.readFileSync(file, "utf8").split(/\r?\n/u);
    lines.forEach((line, index) => {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith("#") || trimmed.startsWith("!")) return;
        const separator = trimmed.search(/[:=]/u);
        if (separator < 1) return;
        const key = trimmed.slice(0, separator).trim();
        if (keys.has(key)) duplicates.push({ key, first: keys.get(key), second: index + 1 });
        else keys.set(key, index + 1);
    });
    return { keys, duplicates };
}

function checkCatalogPair(label, viPath, zhPath, failures) {
    const vi = parseProperties(path.join(ROOT, viPath));
    const zh = parseProperties(path.join(ROOT, zhPath));
    for (const duplicate of [...vi.duplicates, ...zh.duplicates]) {
        failures.push(label + ": key trùng " + duplicate.key + " tại dòng " + duplicate.second);
    }
    const missingZh = [...vi.keys.keys()].filter((key) => !zh.keys.has(key));
    const missingVi = [...zh.keys.keys()].filter((key) => !vi.keys.has(key));
    if (missingZh.length) failures.push(label + ": thiếu key zh_CN: " + missingZh.join(", "));
    if (missingVi.length) failures.push(label + ": thiếu key vi_VN: " + missingVi.join(", "));
    console.log(label + ": " + vi.keys.size + "/" + zh.keys.size + " key");
}

function checkMigration(failures) {
    const seed = fs.readFileSync(path.join(ROOT, "doc/sql/novel_plus.sql"), "utf8");
    const migration = fs.readFileSync(path.join(ROOT, "doc/sql/20260712_vi_localization.sql"), "utf8");
    const menuRows = [...seed.matchAll(/INSERT\s+INTO\s+`?sys_menu`?(?:\s*\([^;]*?\))?\s*VALUES\s*\(\s*'?(\d+)'?\s*,\s*[^,]+,\s*'([^']+)'/gisu)]
        .map((match) => ({ id: match[1], name: match[2] }))
        .filter((row) => HAN.test(row.name));
    for (const row of menuRows) {
        const source = "WHERE menu_id = " + row.id + "\n  AND name = '" + row.name + "';";
        if (!migration.includes(source)) failures.push("Migration thiếu sys_menu " + row.id + " / " + row.name);
    }
    const menuKeys = [...migration.matchAll(/UPDATE\s+sys_menu\s+SET\s+name\s*=\s*'[^']+'\s+WHERE\s+menu_id\s*=\s*(\d+)\s+AND\s+name\s*=\s*'([^']+)'\s*;/gisu)]
        .map((match) => match[1] + "|" + match[2]);
    const duplicateMenuKeys = menuKeys.filter((key, index) => menuKeys.indexOf(key) !== index);
    if (duplicateMenuKeys.length) failures.push("Migration có điều kiện sys_menu trùng: " + [...new Set(duplicateMenuKeys)].join(", "));
    const unsafeUpdates = [...migration.matchAll(/^UPDATE\s+[\s\S]*?;/gimu)].filter((match) => !/\bWHERE\b/iu.test(match[0]));
    if (unsafeUpdates.length) failures.push("Migration có " + unsafeUpdates.length + " UPDATE không có WHERE");
    console.log("migration: " + menuRows.length + " menu seed tiếng Trung đã đối chiếu");
}

function checkSynchronizedAssets(failures) {
    const groups = [
        [
            "novel-front/src/main/resources/static/images/logo.png",
            "templates/green/static/images/logo.png",
            "templates/orange/static/images/logo.png"
        ],
        [
            "novel-front/src/main/resources/static/images/logo_white.png",
            "templates/green/static/images/logo_white.png",
            "templates/orange/static/images/logo_white.png",
            "templates/blue/static/images/logo.png"
        ],
        [
            "templates/dark/static/static/logo.png",
            "templates/dark/static/mobile/static/logo.png",
            "templates/orange/static/mobile/static/logo.png"
        ],
        [
            "templates/dark/static/mang.png",
            "templates/dark/static/mobile/mang.png",
            "templates/orange/static/mobile/mang.png"
        ],
        [
            "novel-front/src/main/resources/static/images/pic_upload.png",
            "templates/green/static/images/pic_upload.png",
            "templates/orange/static/images/pic_upload.png"
        ]
    ];
    for (const group of groups) {
        const hashes = group.map((file) => crypto.createHash("sha256").update(fs.readFileSync(path.join(ROOT, file))).digest("hex"));
        if (new Set(hashes).size !== 1) failures.push("Tài nguyên ảnh chưa đồng bộ: " + group.join(", "));
    }
    console.log("assets: " + groups.length + " nhóm ảnh runtime/theme đã đồng bộ");
}

const allFiles = walk(ROOT);
const textFiles = allFiles.filter(isTextFile);
const failures = [];
let allowedHanFiles = 0;
let parsedJsFiles = 0;
let parsedInlineScripts = 0;

function parseJavaScript(source) {
    if (/^\s*(?:import|export)\s/mu.test(source)) {
        const result = spawnSync(process.execPath, ["--input-type=module", "--check"], {
            input: source,
            encoding: "utf8"
        });
        if (result.error) throw result.error;
        if (result.status !== 0) {
            throw new SyntaxError((result.stderr || result.stdout || "ES module không hợp lệ").trim());
        }
        return;
    }
    new Function(source);
}

for (const absolute of textFiles) {
    const relative = normalizePath(absolute);
    const content = fs.readFileSync(absolute, "utf8");
    if (!HAN.test(content)) continue;
    const fileAllowance = wholeFileAllowance(relative);
    if (fileAllowance) {
        allowedHanFiles++;
        continue;
    }
    const stripped = stripAllowedTokens(content, exactTokenAllowlist.get(relative));
    const match = stripped.match(HAN);
    if (match) failures.push(relative + ": còn chữ Hán ngoài allowlist tại dòng " + lineNumberAt(stripped, match.index));
    else allowedHanFiles++;
}

for (const absolute of allFiles.filter((file) => path.extname(file) === ".js")) {
    const relative = normalizePath(absolute);
    if (wholeFileAllowance(relative) || /\/static\/sql\//u.test("/" + relative)) continue;
    const source = fs.readFileSync(absolute, "utf8");
    try {
        parseJavaScript(source);
        parsedJsFiles++;
    } catch (error) {
        failures.push(relative + ": JavaScript không parse được: " + error.message);
    }
}

for (const absolute of allFiles.filter((file) => path.extname(file) === ".html")) {
    const relative = normalizePath(absolute);
    if (wholeFileAllowance(relative)) continue;
    const html = fs.readFileSync(absolute, "utf8");
    const scriptPattern = /<script\b([^>]*)>([\s\S]*?)<\/script>/giu;
    let scriptMatch;
    let scriptIndex = 0;
    while ((scriptMatch = scriptPattern.exec(html))) {
        scriptIndex++;
        const attributes = scriptMatch[1];
        const source = scriptMatch[2];
        if (/\bsrc\s*=/iu.test(attributes) || /application\/(?:ld\+json|json)/iu.test(attributes) || !source.trim()) continue;
        try {
            parseJavaScript(source);
        } catch (error) {
            failures.push(relative + "#script" + scriptIndex + ": JavaScript template thô không parse được: " + error.message);
            continue;
        }
        const rendered = source
            .replace(/\[\[[\s\S]*?\]\]/gu, "null")
            .replace(/\[\([\s\S]*?\)\]/gu, "null");
        try {
            parseJavaScript(rendered);
            parsedInlineScripts++;
        } catch (error) {
            failures.push(relative + "#script" + scriptIndex + ": JavaScript sau mô phỏng render không parse được: " + error.message);
        }
    }
}

const htmlRoots = [
    "novel-front/src/main/resources/templates",
    "novel-admin/src/main/resources/templates",
    "novel-crawl/src/main/resources/templates",
    "templates"
];
let checkedHtmlRoots = 0;
for (const base of htmlRoots) {
    for (const absolute of walk(path.join(ROOT, base), [])) {
        if (path.extname(absolute) !== ".html") continue;
        const relative = normalizePath(absolute);
        if (wholeFileAllowance(relative)) continue;
        const content = fs.readFileSync(absolute, "utf8");
        const rootTag = content.match(/<html\b[^>]*>/isu);
        if (!rootTag) continue;
        checkedHtmlRoots++;
        if (!/\blang\s*=\s*["']vi(?:-VN)?["']/iu.test(rootTag[0])) failures.push(relative + ": root <html> chưa đặt lang=\"vi\"");
    }
}

checkCatalogPair(
    "common",
    "novel-common/src/main/resources/i18n/common/messages_vi_VN.properties",
    "novel-common/src/main/resources/i18n/common/messages_zh_CN.properties",
    failures
);
checkCatalogPair(
    "front",
    "novel-front/src/main/resources/i18n/messages_vi_VN.properties",
    "novel-front/src/main/resources/i18n/messages_zh_CN.properties",
    failures
);
checkCatalogPair(
    "crawl",
    "novel-crawl/src/main/resources/i18n/messages_vi_VN.properties",
    "novel-crawl/src/main/resources/i18n/messages_zh_CN.properties",
    failures
);
checkCatalogPair(
    "admin",
    "novel-admin/src/main/resources/i18n/messages_vi_VN.properties",
    "novel-admin/src/main/resources/i18n/messages_zh_CN.properties",
    failures
);
checkMigration(failures);
checkSynchronizedAssets(failures);

console.log("scan: " + textFiles.length + " tệp văn bản, " + allowedHanFiles + " tệp có ngoại lệ hợp lệ");
console.log("javascript: " + parsedJsFiles + " tệp first-party parse thành công");
console.log("inline javascript: " + parsedInlineScripts + " khối template parse thành công ở trạng thái thô và mô phỏng render");
console.log("html: " + checkedHtmlRoots + " root tag đã kiểm tra");

if (failures.length) {
    console.error("\nXác minh i18n thất bại:");
    for (const failure of failures) console.error("- " + failure);
    process.exitCode = 1;
} else {
    console.log("\nXác minh i18n thành công.");
}
