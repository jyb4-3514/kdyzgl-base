# apps/ 占位说明

本目录是 `apps/*`（`web` / `staff-h5` / `boss-h5`）的**占位目录**，B1 批次**不建任何实际 app**。

- B1 已包含 `packages/*` 四个共享包；`apps/*` 的目录与 package.json 在 **B3 / B4 / B5** 才真正落地。
- package.json 的 `workspaces` 已声明 `apps/*`，届时新增 `apps/<端>/package.json` 即被自动纳入，无需改根配置。
- 迁移期 `hrm-demo` 冻结、`hrm-admin` 不动，故本目录当前只有本说明文件。
