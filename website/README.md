# 桌面歌词官网

https://luoh-an.github.io/lyrics-android/

原生 HTML/CSS/JavaScript 静态站，无运行时 npm 依赖。参考 Agora 官网的留白和大标题，以独立排版实现。

## 视觉约束

- 所有网页文字至少 18px，禁止小字脚注、英文角标、装饰编号。
- 不堆卡片、网格、轨道、图标、阴影和入场动画。
- 标题使用较轻的可变字重；中文不再沿用拉丁标题的过紧字距。
- 字体、字号、行高、宽度和对齐共同控制版面，不靠增加装饰。
- 兼容性、权限、来源与演示真实性在正常字号 FAQ 展示。应用截图链接到仓库，不内嵌缩略小字图。

## 字体

站点本地托管 Noto Sans SC 可变字体子集，覆盖当前 `index.html` 与 `app.js` 文案，约 114 KB，字重范围 100–900。浏览器从本站加载，不访问 Google Fonts。

- `fonts/lyrics-sans.woff2`：字体文件。
- `fonts/OFL.txt`：随站点一起发布的 SIL OFL 1.1 许可。
- `fonts/glyphs.txt`：请求的字符集。
- `fonts/manifest.json`：官方来源与校验值。

修改文案后可手动运行 `python3 website/scripts/update-font.py` 刷新字体子集。该步骤需要网络，仅开发时执行，**不在部署中下载字体**。未包含的新字符会使用系统后备字体。

## 部署：只构建和发布

`Deploy website` 在 `main` 的官网文件变化后运行，或从 main 手动触发。只有一个部署作业：

1. 检出源码并准备 Node 22。
2. 执行静态构建，复制页面、字体和许可。
3. 上传 GitHub Pages 产物并发布。

**不安装 npm 依赖、不安装浏览器、不跑截图或回归测试、不做多轮公网检查。** PR 不部署。Pages 环境只允许 main；不改域名，不发布 APK。Android `build.yml` 已忽略官网、文档及网页工作流的独立修改；涉及 Android 代码的提交仍正常测试。

如果页面需要回退，恢复对应官网文件并推送 main，再由同一流程发布。官网内容使用相对路径；canonical、og:url 与 sitemap 指向正式地址，变更域名时需同步修改。

## 本地预览

```sh
node website/scripts/build.mjs
node website/scripts/serve.mjs
```

打开 http://127.0.0.1:4173 ，或直接打开 `website/dist/index.html`。预览服务只提供构建产物，不暴露整个仓库。

## 可选检查

完整检查保留为 **Website tests (manual)**，只在 Actions 手动点击时运行，不影响日常部署。它检查多屏宽、18px 最小字号、交互、拖动、键盘、无 JS 降级并输出预览截图。不要在每次文字或样式修改后自动触发。

本地可选运行：

```sh
node website/scripts/check.mjs
cd website
npm ci --ignore-scripts
npx playwright install chromium
npm test
```

## 内容与隐私

网页演示使用原创示例文案，不读取音乐、麦克风或通知；页面无分析脚本、Cookie、持久存储、外部字体请求或自动 API 请求。点击外链才访问对应服务。网站行为与 Android 应用的联网行为是两回事。

维护入口：`index.html`（文案与布局）、`styles.css`（字体与响应式）、`app.js`（交互）、`scripts/`（构建和预览）。保留仓库原有上游署名及许可边界，歌词平台名称不代表合作或兼容保证。
