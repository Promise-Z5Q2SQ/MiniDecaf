# 约定
为了方便测试，请你做实验时，遵守以下约定。

* 测试环境是 java 14，不过 java 向前兼容所以你不必太顾虑这点。

* 使用 gradle 管理依赖和编译。编译命令是 `./gradlew build`，编译结果在 `build/libs/minidecaf.jar`。

| 文件/目录 | 说明 |
| --- | --- |
| `README.md` | 项目说明。随便改。 |
| `.gitlab-ci.yml` | 自动测试配置， **不能改动！** |
| `prepare.sh` | 运行测试前你要运行的命令，例如运行 parser generator。可以改，只要保证能被 bash 运行即可。 |
| `step-until.txt` | 告诉自动测试，你做到哪个 step 了。可以改，须保证内容是 1 到 12 的一个整数。 **做完每个 step 后请及时修改，避免影响评分！** |
| `reports/` | 实验报告，使用 pdf 或 md 格式，命名格式如 `step1.pdf`、`step2.md` 等。 |
| `gradlew` | gradle wrapper，默认版本 6.5.1， **不建议修改！** |
| `build.gradle` | gradle 编译脚本，来自谢兴宇助教的参考框架，其中已写好了 ANTLR 插件用法和 jar 包的正确生成方式，你可以对其作你需要的修改，例如添加所需的依赖等 |
| `src/` | 放你的编译器代码。 |
| 其他文件 | **不建议修改！** |

你的依赖都需要从 gradle 安装，自动测试不一定支持其他依赖安装。

# 评分
MiniDecaf 有 6 个阶段，每个阶段的 ddl 截止时，我们会检查你最后一次通过 CI 的 commit。

* 如果 `.gitlab-ci.yml` 没有改动，并且 `step-until.txt` 中的数字大于等于那个阶段的最后一个 step 编号，我们就认为你按时完成了该阶段任务。

* 否则，我们会等待你通过该阶段任务，并且按照指导书所说折算晚交扣分。
