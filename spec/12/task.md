# SubAgent 涓庡悗鍙颁换鍔?Tasks

## 鏂囦欢娓呭崟

| 鎿嶄綔 | 鏂囦欢 | 鑱岃矗 |
|------|------|------|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinition.java` | 瀹氫箟寮忓瓙 Agent 瑙掕壊鏁版嵁缁撴瀯 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinitionCatalog.java` | 瑙掕壊鐩綍鎺ュ彛 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinitionCatalogSnapshot.java` | 瑙掕壊鐩綍蹇収 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinitionCandidate.java` | 瑙掕壊瀹氫箟鍊欓€夎祫婧?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinitionDiagnostic.java` | 瑙掕壊鍔犺浇璇婃柇 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinitionParser.java` | 瑙掕壊瑙ｆ瀽鎺ュ彛 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinitionParseResult.java` | 瑙掕壊瑙ｆ瀽缁撴灉 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinitionSource.java` | 瑙掕壊鏉ユ簮鎺ュ彛 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentDefinitionSourceKind.java` | 瑙掕壊鏉ユ簮绫诲瀷 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/BuiltinAgentDefinitionSource.java` | 鍐呯疆瑙掕壊鏉ユ簮 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/DefaultAgentDefinitionCatalog.java` | 瑙掕壊鍙戠幇銆佹牎楠屻€佸悎骞?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/FileSystemAgentDefinitionSource.java` | 椤圭洰绾у拰鐢ㄦ埛绾ц鑹叉潵婧?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/FrontmatterAgentDefinitionParser.java` | Markdown frontmatter 瑙ｆ瀽 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/PluginAgentDefinitionSource.java` | 鎻掍欢瑙掕壊鏉ユ簮鍗犱綅 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/SubAgentKind.java` | 瀹氫箟寮忓拰 Fork 寮忔灇涓?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/SubAgentLaunchRequest.java` | 瀛?Agent 鍚姩璇锋眰 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/SubAgentParentContext.java` | 鐖?Agent 杩愯涓婁笅鏂?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/SubAgentResult.java` | 瀛?Agent 缁撴灉 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/SubAgentRunHandle.java` | 杩愯涓瓙 Agent 鍙ユ焺 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/DefaultSubAgentRunHandle.java` | 榛樿杩愯鍙ユ焺瀹炵幇 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/SubAgentRunnerFactory.java` | 瀛?Agent 杩愯鍣ㄥ伐鍘傛帴鍙?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/DefaultSubAgentRunnerFactory.java` | 瀛?Agent 杩愯鍣ㄥ伐鍘傚疄鐜?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/SubAgentService.java` | 瀛?Agent 璋冨害鏈嶅姟鎺ュ彛 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/DefaultSubAgentService.java` | 瀛?Agent 璋冨害鏈嶅姟瀹炵幇 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/ToolPolicyResolver.java` | 瀛?Agent 宸ュ叿杩囨护 |
| 鏂板缓 | `src/main/java/com/lunacode/subagent/SubAgentModelResolver.java` | 妯″瀷缁ф壙鍜屽埆鍚嶈В鏋?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/AgentExecutionContextHolder.java` | 宸ュ叿鎵ц鏈熺埗涓婁笅鏂囦紶閫?|
| 鏂板缓 | `src/main/java/com/lunacode/subagent/DenyingPermissionConfirmationBroker.java` | 闈炰氦浜掓潈闄愭嫆缁?|
| 鏂板缓 | `src/main/java/com/lunacode/background/BackgroundTask.java` | 鍚庡彴浠诲姟鍐呴儴鐘舵€?|
| 鏂板缓 | `src/main/java/com/lunacode/background/BackgroundTaskManager.java` | 鍚庡彴浠诲姟绠＄悊鎺ュ彛 |
| 鏂板缓 | `src/main/java/com/lunacode/background/BackgroundTaskSnapshot.java` | 鍚庡彴浠诲姟鍙蹇収 |
| 鏂板缓 | `src/main/java/com/lunacode/background/BackgroundTaskStatus.java` | 鍚庡彴浠诲姟鐘舵€?|
| 鏂板缓 | `src/main/java/com/lunacode/background/BackgroundTaskListener.java` | 鍚庡彴浠诲姟瀹屾垚鐩戝惉 |
| 鏂板缓 | `src/main/java/com/lunacode/background/DefaultBackgroundTaskManager.java` | 鍚庡彴浠诲姟绠＄悊瀹炵幇 |
| 鏂板缓 | `src/main/java/com/lunacode/background/ForegroundSubAgentTracker.java` | 鍓嶅彴瀛?Agent 杩借釜鎺ュ彛 |
| 鏂板缓 | `src/main/java/com/lunacode/background/DefaultForegroundSubAgentTracker.java` | 鍓嶅彴瀛?Agent 杩借釜瀹炵幇 |
| 鏂板缓 | `src/main/java/com/lunacode/background/ProgressTracker.java` | 宸ュ叿璋冪敤銆乼oken 鍜屾渶杩戞椿鍔ㄨ拷韪?|
| 鏂板缓 | `src/main/java/com/lunacode/background/TaskNotificationFormatter.java` | `<task-notification>` 鏍煎紡鍖?|
| 鏂板缓 | `src/main/java/com/lunacode/tool/AgentTool.java` | 妯″瀷鍙皟鐢ㄧ殑缁熶竴 Agent 宸ュ叿 |
| 鏂板缓 | `src/main/java/com/lunacode/hook/RealSubAgentHookActionExecutor.java` | Hook 瀛?Agent 鐪熷疄鎵ц鍣?|
| 淇敼 | `src/main/java/com/lunacode/config/AgentConfig.java` | 澧炲姞妯″瀷鍒悕鍜岃嚜鍔ㄥ悗鍙伴槇鍊奸厤缃?|
| 淇敼 | `src/main/java/com/lunacode/config/ConfigLoader.java` | 瑙ｆ瀽鏂板 agent 閰嶇疆 |
| 淇敼 | `config.example.yaml` | 琛ュ厖鏂板閰嶇疆绀轰緥 |
| 淇敼 | `src/main/java/com/lunacode/skill/ToolAccessPolicy.java` | 鏀寔榛戝悕鍗曚紭鍏堝拰鍚庡彴瀹夊叏杩囨护 |
| 淇敼 | `src/main/java/com/lunacode/runtime/AgentRunConfig.java` | 鎼哄甫瀛?Agent 杩愯鏍囪鍜屽伐鍏风瓥鐣?|
| 淇敼 | `src/main/java/com/lunacode/agent/execution/AgentToolRunner.java` | 娉ㄥ叆鎵ц涓婁笅鏂囥€佽拷韪繘搴︺€佹墽琛屽眰杩囨护 |
| 淇敼 | `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` | 涓哄伐鍏锋墽琛屾湡鎻愪緵鐖朵笂涓嬫枃 |
| 淇敼 | `src/main/java/com/lunacode/orchestrator/ChatOrchestrator.java` | 澧炲姞 ESC 鍓嶅彴杞悗鍙板叆鍙?|
| 淇敼 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 娉ㄥ唽鍚庡彴鐩戝惉銆佹敞鍏ラ€氱煡銆佹帴鍏ュ墠鍙拌浆鍚庡彴 |
| 淇敼 | `src/main/java/com/lunacode/tui/LanternaLunaTui.java` | ESC 蹇欑鏃朵紭鍏堝垏鍚庡彴 |
| 淇敼 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 缁勮瑙掕壊鐩綍銆佸悗鍙扮鐞嗗櫒銆丄gent 宸ュ叿鍜?Hook 鎵ц鍣?|
| 鏂板缓 | `src/test/java/com/lunacode/subagent/*Test.java` | 瀛?Agent 鍗曞厓娴嬭瘯 |
| 鏂板缓 | `src/test/java/com/lunacode/background/*Test.java` | 鍚庡彴浠诲姟鍗曞厓娴嬭瘯 |
| 鏂板缓 | `src/test/java/com/lunacode/tool/AgentToolTest.java` | Agent 宸ュ叿鍗曞厓娴嬭瘯 |
| 鏂板缓 | `src/test/java/com/lunacode/hook/RealSubAgentHookActionExecutorTest.java` | Hook 瀛?Agent 鎵ц娴嬭瘯 |

## T1: 鎵╁睍 Agent 閰嶇疆缁撴瀯

**鏂囦欢锛?* `src/main/java/com/lunacode/config/AgentConfig.java`銆乣src/main/java/com/lunacode/config/ConfigLoader.java`銆乣config.example.yaml`  
**渚濊禆锛?* 鏃? 
**姝ラ锛?*
1. 鍦?`AgentConfig` 涓鍔?`autoBackgroundMs` 瀛楁锛岄粯璁ゅ€间负 `120000`銆?2. 鍦?`AgentConfig` 涓鍔?`modelAliases` 鏄犲皠锛屾敮鎸?`sonnet`銆乣opus`銆乣haiku`銆?3. 鍦?`ConfigLoader` 鐨?agent 鑺傜偣瑙ｆ瀽涓鍙?`auto_background_ms` 鍜?`model_aliases`銆?4. 鍦?`config.example.yaml` 涓ˉ鍏呴厤缃ず渚嬪拰榛樿璇存槑銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=ConfigLoaderTest`锛屾湡鏈涙柊澧為厤缃彲琚В鏋愶紝缂虹渷閰嶇疆浣跨敤榛樿鍊笺€?
## T2: 鎵╁睍 ToolAccessPolicy 鏀寔榛戝悕鍗?
**鏂囦欢锛?* `src/main/java/com/lunacode/skill/ToolAccessPolicy.java`銆乣src/test/java/com/lunacode/skill/ToolAccessPolicyTest.java`  
**渚濊禆锛?* 鏃? 
**姝ラ锛?*
1. 涓?`ToolAccessPolicy` 澧炲姞 `deniedTools` 闆嗗悎銆?2. 淇敼 `allows`锛屽厛妫€鏌?`deniedTools`锛屽懡涓垯杩斿洖 false銆?3. 淇濈暀 `alwaysVisibleTools` 瀵圭郴缁熷姞杞藉伐鍏风殑璇箟锛屼絾鍚庡彴瀹夊叏灞傚彲鏄惧紡绂佹帀 `Agent`銆?4. 涓哄彧鐧藉悕鍗曘€佸彧榛戝悕鍗曘€佺櫧鍚嶅崟榛戝悕鍗曞啿绐併€乤lways visible 鍥涚被鍦烘櫙琛ユ祴璇曘€?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=ToolAccessPolicyTest`锛屾湡鏈涢粦鍚嶅崟浼樺厛銆?
## T3: 鏂板缓瑙掕壊瀹氫箟鍩虹鏁版嵁缁撴瀯

**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/AgentDefinition.java`銆乣AgentDefinitionSourceKind.java`銆乣AgentDefinitionDiagnostic.java`銆乣AgentDefinitionCatalogSnapshot.java`  
**渚濊禆锛?* T1  
**姝ラ锛?*
1. 瀹氫箟 `AgentDefinitionSourceKind`锛屽寘鍚?`PROJECT`銆乣USER`銆乣BUILTIN`銆乣PLUGIN`銆?2. 瀹氫箟 `AgentDefinition` record锛屽瓧娈典笌 plan 涓繚鎸佷竴鑷淬€?3. 瀹氫箟璇婃柇鍜屽揩鐓?record銆?4. 鍦ㄦ瀯閫犲櫒涓鑼冨寲绌哄瓧绗︿覆銆佺┖鍒楄〃銆佽矾寰勫拰榛樿妯″瀷銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=AgentDefinitionTest`锛屾湡鏈涢粯璁ゅ€煎拰瀛楁瑙勮寖鍖栨纭€?
## T4: 鏂板缓瑙掕壊鍊欓€夊拰鏉ユ簮鎺ュ彛

**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/AgentDefinitionCandidate.java`銆乣AgentDefinitionSource.java`銆乣FileSystemAgentDefinitionSource.java`  
**渚濊禆锛?* T3  
**姝ラ锛?*
1. 瀹氫箟 `AgentDefinitionCandidate`锛屾敮鎸佸崟鏂囦欢鍊欓€夊拰鍐呭瓨鍐呭鍊欓€夈€?2. 瀹氫箟 `AgentDefinitionSource.discover(projectRoot, userHome)`銆?3. 瀹炵幇椤圭洰绾ф壂鎻?`.lunacode/agents/*.md`銆?4. 瀹炵幇鐢ㄦ埛绾ф壂鎻?`~/.lunacode/agents/*.md`銆?5. 鎵弿鏃舵寜鏂囦欢鍚嶇ǔ瀹氭帓搴忋€?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=FileSystemAgentDefinitionSourceTest`锛屾湡鏈涜兘鍙戠幇椤圭洰绾у拰鐢ㄦ埛绾?Markdown 鏂囦欢銆?
## T5: 鏂板缓鍐呯疆鍜屾彃浠惰鑹叉潵婧?
**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/BuiltinAgentDefinitionSource.java`銆乣PluginAgentDefinitionSource.java`  
**渚濊禆锛?* T4  
**姝ラ锛?*
1. 瀹炵幇 `BuiltinAgentDefinitionSource`锛岀涓€鐗堝厑璁歌繑鍥炵┖鍒楄〃鎴栧皯閲忓唴缃牱渚嬨€?2. 瀹炵幇 `PluginAgentDefinitionSource`锛岀涓€鐗堣繑鍥炵┖鍒楄〃骞朵繚鐣欐潵婧愮被鍨嬨€?3. 纭繚涓よ€呬笉浼氬洜涓鸿祫婧愮己澶辨姏鍑哄惎鍔ㄥ紓甯搞€?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=BuiltinAgentDefinitionSourceTest,PluginAgentDefinitionSourceTest`锛屾湡鏈涚┖璧勬簮鏃惰繑鍥炵┖鍒楄〃銆?
## T6: 瀹炵幇瑙掕壊 frontmatter 瑙ｆ瀽

**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/AgentDefinitionParser.java`銆乣AgentDefinitionParseResult.java`銆乣FrontmatterAgentDefinitionParser.java`  
**渚濊禆锛?* T3銆乀4  
**姝ラ锛?*
1. 澶嶇敤 Skill parser 鐨?frontmatter 鍒嗗壊鎬濊矾銆?2. 鏍￠獙 `name` 鍜?`description` 蹇呭～銆?3. 瑙ｆ瀽 `tools`銆乣disallowedTools`銆乣model`銆乣maxTurns`銆乣permissionMode`銆?4. 鎶?Markdown body 鍐欏叆 `systemPrompt`銆?5. 瀵归潪娉?YAML銆侀潪娉曞瓧娈电被鍨嬪拰闈炴硶鏉冮檺妯″紡杩斿洖 failure銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=FrontmatterAgentDefinitionParserTest`锛屾湡鏈涙湁鏁堢ず渚嬭В鏋愭垚鍔燂紝鍧?frontmatter 杩斿洖 failure銆?
## T7: 瀹炵幇瑙掕壊鐩綍鍚堝苟鍜岃瘖鏂?
**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/AgentDefinitionCatalog.java`銆乣DefaultAgentDefinitionCatalog.java`  
**渚濊禆锛?* T4銆乀5銆乀6  
**姝ラ锛?*
1. 鎸夋彃浠躲€佸唴缃€佺敤鎴枫€侀」鐩殑鍙戠幇浼樺厛绾ц鍙栧€欓€夛紝浣块珮浼樺厛绾у悗瑕嗙洊浣庝紭鍏堢骇銆?2. 鏍￠獙 `tools` 鍜?`disallowedTools` 寮曠敤鐨勫伐鍏峰悕鏄惁瀛樺湪銆?3. 鏍￠獙妯″瀷鍒悕鏄惁鍙В鏋愩€?4. 鍗曚釜瀹氫箟澶辫触鏃惰拷鍔?warning 璇婃柇骞惰烦杩囥€?5. 鏆撮湶 `snapshot()`銆乣find()`銆乣diagnostics()`銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultAgentDefinitionCatalogTest`锛屾湡鏈涘悓鍚嶈鐩栥€佸潖瀹氫箟璺宠繃鍜?warning 杈撳嚭姝ｇ‘銆?
## T8: 瀹炵幇妯″瀷瑙ｆ瀽鍣?
**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/SubAgentModelResolver.java`  
**渚濊禆锛?* T1銆乀3  
**姝ラ锛?*
1. 瑙ｆ瀽 `inherit` 鍜岀┖鍊间负鐖舵ā鍨嬨€?2. 瑙ｆ瀽 `sonnet`銆乣opus`銆乣haiku` 涓?`AgentConfig.modelAliases` 涓殑鍏蜂綋妯″瀷銆?3. 瀵圭己澶卞埆鍚嶈繑鍥炲彲璇婃柇閿欒銆?4. 鍏佽瀹屾暣妯″瀷鍚嶇洿閫氥€?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=SubAgentModelResolverTest`锛屾湡鏈涚户鎵裤€佸埆鍚嶃€佸畬鏁存ā鍨嬪悕鍜岀己澶卞埆鍚嶅満鏅€氳繃銆?
## T9: 瀹炵幇宸ュ叿绛栫暐瑙ｆ瀽鍣?
**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/ToolPolicyResolver.java`  
**渚濊禆锛?* T2銆乀3銆乀7  
**姝ラ锛?*
1. 鎺ユ敹鐖剁瓥鐣ャ€佽鑹插畾涔夊拰杩愯鑼冨洿銆?2. 鏈?`tools` 鏃朵互鐧藉悕鍗曚负鍩虹锛涙棤 `tools` 鏃朵互鐖跺彲鐢ㄥ伐鍏烽泦涓哄熀纭€銆?3. 绉婚櫎 `disallowedTools`銆?4. 搴旂敤鍏ㄥ眬绂佹宸ュ叿鍜屽悗鍙板畨鍏ㄧ姝㈠伐鍏枫€?5. Fork 鍜屽悗鍙拌寖鍥村繀椤荤Щ闄?`Agent`銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=ToolPolicyResolverTest`锛屾湡鏈涢粦鍚嶅崟浼樺厛鍜屽悗鍙扮鐢?`Agent` 鐢熸晥銆?
## T10: 鏂板缓鍚庡彴浠诲姟鍩虹缁撴瀯

**鏂囦欢锛?* `src/main/java/com/lunacode/background/BackgroundTaskStatus.java`銆乣BackgroundTask.java`銆乣BackgroundTaskSnapshot.java`銆乣ProgressTracker.java`  
**渚濊禆锛?* 鏃? 
**姝ラ锛?*
1. 瀹氫箟 `BackgroundTaskStatus` 涓夋€併€?2. 瀹氫箟 `ProgressTracker`锛屾敮鎸佽褰曞伐鍏疯皟鐢ㄦ鏁般€乼oken 娑堣€楀拰鏈€杩戞椿鍔ㄣ€?3. 瀹氫箟 `BackgroundTask` 鍐呴儴鍙彉鐘舵€併€?4. 瀹氫箟 `BackgroundTaskSnapshot` 鍙瑙嗗浘銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=BackgroundTaskTest,ProgressTrackerTest`锛屾湡鏈涚姸鎬佸拰杩涘害鏇存柊姝ｇ‘銆?
## T11: 瀹炵幇鍚庡彴浠诲姟绠＄悊鍣?launch

**鏂囦欢锛?* `src/main/java/com/lunacode/background/BackgroundTaskManager.java`銆乣DefaultBackgroundTaskManager.java`銆乣BackgroundTaskListener.java`  
**渚濊禆锛?* T10  
**姝ラ锛?*
1. 瀹氫箟 `launch`銆乣get`銆乣list`銆乣addListener` 鎺ュ彛銆?2. `launch` 鐢熸垚鍞竴浠诲姟 id 骞剁櫥璁?`RUNNING`銆?3. 浣跨敤鍚庡彴 executor 绛夊緟 `SubAgentRunHandle.completion()`銆?4. 瀹屾垚鏃惰缃?`COMPLETED`銆佺粨鏋滃拰缁撴潫鏃堕棿銆?5. 寮傚父鏃惰缃?`FAILED` 鍜屽け璐ュ師鍥犮€?6. 瀹屾垚鎴栧け璐ュ悗閫氱煡鎵€鏈?listener銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultBackgroundTaskManagerTest`锛屾湡鏈涙垚鍔熴€佸け璐ュ拰 listener 閫氱煡閮芥纭€?
## T12: 瀹炵幇杩愯涓换鍔℃帴绠?
**鏂囦欢锛?* `src/main/java/com/lunacode/background/DefaultBackgroundTaskManager.java`銆乣ForegroundSubAgentTracker.java`銆乣DefaultForegroundSubAgentTracker.java`  
**渚濊禆锛?* T10銆乀11  
**姝ラ锛?*
1. 鍦?`BackgroundTaskManager` 澧炲姞 `adoptRunning(handle, task)`銆?2. `adoptRunning` 涓嶅惎鍔ㄦ柊 handle锛屽彧鐧昏宸叉湁 handle 骞剁瓑寰呭叾瀹屾垚銆?3. 瀹炵幇 `ForegroundSubAgentTracker` 淇濆瓨褰撳墠鍓嶅彴瀛?Agent handle銆?4. 瀹炵幇 `adoptCurrentToBackground`锛屾垚鍔熸椂杩斿洖 task id銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultForegroundSubAgentTrackerTest,DefaultBackgroundTaskManagerTest`锛屾湡鏈涙帴绠″悗鍘?handle 瀹屾垚鑳芥洿鏂颁换鍔＄姸鎬併€?
## T13: 瀹炵幇鍚庡彴閫氱煡鏍煎紡鍖?
**鏂囦欢锛?* `src/main/java/com/lunacode/background/TaskNotificationFormatter.java`  
**渚濊禆锛?* T10  
**姝ラ锛?*
1. 鏍煎紡鍖?`<task-notification>` 寮€濮嬪拰缁撴潫鏍囩銆?2. 鍐欏叆 task id銆佺姸鎬併€佹憳瑕佸拰瀹屾暣缁撴灉銆?3. 澶辫触浠诲姟鍐欏叆澶辫触鍘熷洜銆?4. 涓嶅啓鍏ヤ腑闂翠簨浠躲€佸伐鍏疯皟鐢ㄨ浆褰曞拰鏉冮檺璁板綍銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=TaskNotificationFormatterTest`锛屾湡鏈涜緭鍑哄寘鍚畬鏁寸粨鏋滀笖涓嶅寘鍚腑闂磋浆褰曞瓧娈点€?
## T14: 鏂板缓瀛?Agent 鍚姩鏁版嵁缁撴瀯

**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/SubAgentKind.java`銆乣SubAgentLaunchRequest.java`銆乣SubAgentParentContext.java`銆乣SubAgentResult.java`銆乣SubAgentRunHandle.java`銆乣DefaultSubAgentRunHandle.java`  
**渚濊禆锛?* T3銆乀10  
**姝ラ锛?*
1. 瀹氫箟 `SubAgentKind` 鐨?`DEFINED` 鍜?`FORK`銆?2. 瀹氫箟鍚姩璇锋眰銆佺埗涓婁笅鏂囧拰缁撴灉 record銆?3. 瀹炵幇榛樿杩愯 handle锛屽寘瑁?`CompletableFuture<SubAgentResult>`銆佸彇娑堜护鐗屽拰杩涘害杩借釜鍣ㄣ€?4. 鏀寔璁板綍宸茶鍚庡彴浠诲姟鎺ョ鐨?task id銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultSubAgentRunHandleTest`锛屾湡鏈?completion銆乧ancel 鍜?adopted 鐘舵€佹纭€?
## T15: 瀹炵幇闈炰氦浜掓潈闄愮‘璁?Broker

**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/DenyingPermissionConfirmationBroker.java`  
**渚濊禆锛?* 鏃? 
**姝ラ锛?*
1. 瀹炵幇 `PermissionConfirmationBroker`銆?2. 浠绘剰纭璇锋眰鐩存帴杩斿洖 `PermissionConfirmationAnswer.DENY`銆?3. 璁板綍鏈€杩戞嫆缁濆師鍥狅紝渚涚粨鏋滄垨娴嬭瘯瑙傚療銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DenyingPermissionConfirmationBrokerTest`锛屾湡鏈涚‘璁よ姹備笉浼氶樆濉炲苟鐩存帴鎷掔粷銆?
## T16: 澧炲姞 Agent 鎵ц涓婁笅鏂囦紶閫?
**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/AgentExecutionContextHolder.java`銆乣src/main/java/com/lunacode/agent/execution/AgentToolRunner.java`銆乣src/main/java/com/lunacode/agent/DefaultAgentLoop.java`  
**渚濊禆锛?* T14  
**姝ラ锛?*
1. 鏂板缓 thread-local holder锛屾敮鎸?`withContext` 鍜?`current`銆?2. 鍦?`DefaultAgentLoop` 鏋勯€?`SubAgentParentContext` 鎵€闇€鐨勭埗淇℃伅銆?3. 鍦?`AgentToolRunner.executeOne` 鎵ц宸ュ叿鍓嶈缃綋鍓嶄笂涓嬫枃銆?4. 骞惰宸ュ叿 future 鍐呬篃璁剧疆鍚屼竴涓婁笅鏂囥€?5. 鏅€氬伐鍏锋墽琛岀粨鏉熷悗娓呯悊 thread-local銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=AgentExecutionContextHolderTest,AgentToolRunnerTest`锛屾湡鏈?`AgentTool` 鑳借鍙栫埗涓婁笅鏂囷紝鏅€氬伐鍏蜂笉鍙楁薄鏌撱€?
## T17: 鎵╁睍 AgentRunConfig 瀛?Agent 鏍囪

**鏂囦欢锛?* `src/main/java/com/lunacode/runtime/AgentRunConfig.java`  
**渚濊禆锛?* T14  
**姝ラ锛?*
1. 澧炲姞 `parentIsBackground`銆乣parentIsFork` 鎴栫瓑浠峰瓙 Agent 杩愯鏍囪銆?2. 澧炲姞 builder 椋庢牸鏂规硶璁剧疆瀛?Agent 杩愯鏍囪銆?3. 淇濇寔鐜版湁鏋勯€犲櫒鍏煎銆?4. 纭繚榛樿涓?Agent 鏍囪涓洪潪鍚庡彴銆侀潪 Fork銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=AgentRunConfigTest`锛屾湡鏈涙棫鏋勯€犲櫒鍜屾柊鏍囪閮芥甯搞€?
## T18: 瀹炵幇瀛?Agent 杩愯鍣ㄥ伐鍘傜殑瀹氫箟寮忚矾寰?
**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/SubAgentRunnerFactory.java`銆乣DefaultSubAgentRunnerFactory.java`  
**渚濊禆锛?* T8銆乀9銆乀14銆乀15銆乀16銆乀17  
**姝ラ锛?*
1. 涓哄畾涔夊紡鍒涘缓鏂扮殑 `DefaultConversationManager`銆?2. 灏嗚鑹茬郴缁熸彁绀哄姞鍏?`SkillPromptContext` 鎴栫瓑浠?prompt 鎵╁睍鐐广€?3. 鎸夎鑹茶В鏋愭ā鍨嬨€佹潈闄愭ā寮忋€佹渶澶ц疆娆″拰宸ュ叿绛栫暐銆?4. 浣跨敤 `DenyingPermissionConfirmationBroker` 鍒涘缓瀛?Agent 鐨勫伐鍏?runner銆?5. 鍦ㄧ嫭绔?executor 涓皟鐢?`DefaultAgentLoop.run` 鍒?completion銆?6. 浠庡瓙瀵硅瘽鏈€鍚庝竴鏉″畬鏁?assistant 娑堟伅鐢熸垚 `SubAgentResult`銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest`锛屾湡鏈涘畾涔夊紡瀛?Agent 涓嶅寘鍚埗鍘嗗彶锛屽苟鑳借繑鍥炴渶缁堢粨鏋溿€?
## T19: 瀹炵幇瀛?Agent 杩愯鍣ㄥ伐鍘傜殑 Fork 璺緞

**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/DefaultSubAgentRunnerFactory.java`  
**渚濊禆锛?* T18  
**姝ラ锛?*
1. 浠庣埗 `ConversationCompactionAccess.fullSnapshot()` 璇诲彇鍘熷娑堟伅銆?2. 鎸夊師椤哄簭澶嶅埗鐢ㄦ埛銆乤ssistant 鍜屽伐鍏风粨鏋滄秷鎭埌瀛愬璇濄€?3. 缁ф壙鐖跺伐鍏风瓥鐣ュ悗搴旂敤鍚庡彴瀹夊叏杩囨护銆?4. 鏍囪瀛愯繍琛岃寖鍥翠负 Fork 鍜屽悗鍙般€?5. 浣跨敤鏈 task 浣滀负鏂扮敤鎴疯緭鍏ュ惎鍔ㄥ瓙 Agent銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest#forkCopiesParentHistory`锛屾湡鏈涙秷鎭『搴忓拰鍐呭淇濇寔涓€鑷达紝涓?`Agent` 宸ュ叿琚鐢ㄣ€?
## T20: 瀹炵幇杩涘害杩借釜浜嬩欢姹囨€?
**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/DefaultSubAgentRunnerFactory.java`銆乣src/main/java/com/lunacode/background/ProgressTracker.java`  
**渚濊禆锛?* T18  
**姝ラ锛?*
1. 涓哄瓙 Agent 鍒涘缓浜嬩欢 sink 鍖呰鍣ㄣ€?2. 鏀跺埌宸ュ叿寮€濮嬫垨宸ュ叿缁撴灉浜嬩欢鏃舵洿鏂板伐鍏疯皟鐢ㄦ鏁板拰鏈€杩戞椿鍔ㄣ€?3. 鏀跺埌 usage 浜嬩欢鏃剁疮鍔?token 娑堣€椼€?4. 涓嶆妸瀛?Agent 浜嬩欢鍐欏叆涓诲璇濄€?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=ProgressTrackerTest,DefaultSubAgentRunnerFactoryTest#tracksProgress`锛屾湡鏈涘伐鍏锋鏁般€乼oken 鍜屾渶杩戞椿鍔ㄦ洿鏂般€?
## T21: 瀹炵幇 SubAgentService 鐨勫垎娴侀€昏緫

**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/SubAgentService.java`銆乣DefaultSubAgentService.java`  
**渚濊禆锛?* T7銆乀11銆乀12銆乀18銆乀19  
**姝ラ锛?*
1. `subagent_type` 涓虹┖鏃跺垱寤?Fork 璇锋眰銆?2. Fork 璇锋眰寮哄埗璋冪敤 `BackgroundTaskManager.launch`銆?3. `subagent_type` 闈炵┖鏃舵煡璇㈣鑹茬洰褰曘€?4. 瑙掕壊涓嶅瓨鍦ㄦ椂杩斿洖宸ュ叿閿欒缁撴灉銆?5. 瀹氫箟寮忔牴鎹?`run_in_background` 閫夋嫨鍚庡彴鎴栧墠鍙拌繍琛屻€?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultSubAgentServiceTest`锛屾湡鏈?Fork 寮哄埗鍚庡彴銆佸畾涔夊紡鏌ユ壘鍜岃鑹蹭笉瀛樺湪閿欒姝ｇ‘銆?
## T22: 瀹炵幇鑷姩瓒呮椂杞悗鍙?
**鏂囦欢锛?* `src/main/java/com/lunacode/subagent/DefaultSubAgentService.java`銆乣src/main/java/com/lunacode/background/DefaultForegroundSubAgentTracker.java`  
**渚濊禆锛?* T12銆乀21  
**姝ラ锛?*
1. 鍓嶅彴瀹氫箟寮忓惎鍔ㄥ悗鐧昏鍒?`ForegroundSubAgentTracker`銆?2. 浣跨敤 `AgentConfig.autoBackgroundMs` 绛夊緟 completion銆?3. 瓒呮椂鏃惰皟鐢?`BackgroundTaskManager.adoptRunning`銆?4. 杩斿洖 `async_launched` 鍜?task id銆?5. 姝ｅ父瀹屾垚鏃舵竻鐞嗗墠鍙?tracker銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultSubAgentServiceTest#autoBackgroundAfterTimeout`锛屾湡鏈涜秴鏃跺悗鍚屼竴 handle 琚帴绠°€?
## T23: 瀹炵幇 AgentTool

**鏂囦欢锛?* `src/main/java/com/lunacode/tool/AgentTool.java`  
**渚濊禆锛?* T16銆乀21銆乀22  
**姝ラ锛?*
1. 瀹炵幇 `Tool` 鎺ュ彛锛屽悕绉板浐瀹氫负 `Agent`銆?2. 瀹氫箟 input schema锛歚task` 蹇呭～锛宍subagent_type` 鍙€夛紝`run_in_background` 鍙€夈€?3. 鏍￠獙 task 闈炵┖銆?4. 浠?`AgentExecutionContextHolder.current()` 璇诲彇鐖朵笂涓嬫枃銆?5. 璋冪敤 `SubAgentService.launchFromTool`銆?6. 灏嗗墠鍙扮粨鏋溿€佸悗鍙板惎鍔ㄧ姸鎬佹垨閿欒灏佽涓?`ToolResult`銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=AgentToolTest`锛屾湡鏈?schema銆佸弬鏁版牎楠屻€丗ork 榛樿鍜屽悗鍙拌繑鍥炴牸寮忔纭€?
## T24: 鍦ㄦ墽琛屽眰闃叉瀛?Agent 宓屽

**鏂囦欢锛?* `src/main/java/com/lunacode/agent/execution/AgentToolRunner.java`銆乣src/main/java/com/lunacode/subagent/ToolPolicyResolver.java`  
**渚濊禆锛?* T9銆乀16銆乀17銆乀23  
**姝ラ锛?*
1. 璁╁悗鍙板拰 Fork 瀛?Agent 鐨勫伐鍏峰０鏄庝腑涓嶅嚭鐜?`Agent`銆?2. 鍦ㄦ墽琛屽眰鍐嶆妫€鏌?`Agent` 鏄惁琚綋鍓嶇瓥鐣ョ姝€?3. Fork 寮忓瓙 Agent 灏濊瘯鍐?Fork 鏃惰繑鍥炲彲璇婃柇宸ュ叿閿欒銆?4. 鍚庡彴 Agent 灏濊瘯 spawn 浠绘剰 Agent 鏃惰繑鍥炲彲璇婃柇宸ュ叿閿欒銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=AgentToolRunnerTest,ToolPolicyResolverTest`锛屾湡鏈涘０鏄庡眰鍜屾墽琛屽眰閮芥嫤鎴祵濂椼€?
## T25: 瀹炵幇 Hook 瀛?Agent 鎵ц鍣?
**鏂囦欢锛?* `src/main/java/com/lunacode/hook/RealSubAgentHookActionExecutor.java`  
**渚濊禆锛?* T21銆乀23  
**姝ラ锛?*
1. 瀹炵幇 `HookActionExecutor`銆?2. 鏍￠獙 action 涓?`HookAction.SubAgent`銆?3. 浣跨敤 action 鐨?`name` 浣滀负 `subagent_type`锛宍prompt` 浣滀负 task銆?4. 璋冪敤 `SubAgentService.launchFromHook` 鍚庡彴鍚姩浠诲姟銆?5. 鎴愬姛杩斿洖鍖呭惈 task id 鐨?`HookActionResult`銆?6. 澶辫触杩斿洖 failure锛屼氦缁?Hook 鏃ュ織璁板綍銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=RealSubAgentHookActionExecutorTest`锛屾湡鏈涙湁鏁?Hook 鍚姩鍚庡彴浠诲姟锛屼笉瀛樺湪瑙掕壊杩斿洖澶辫触缁撴灉銆?
## T26: 淇敼 orchestrator 娉ㄥ叆鍚庡彴閫氱煡

**鏂囦欢锛?* `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`銆乣src/main/java/com/lunacode/background/TaskNotificationFormatter.java`  
**渚濊禆锛?* T11銆乀13  
**姝ラ锛?*
1. 璁?`DefaultChatOrchestrator` 娉ㄥ唽 `BackgroundTaskListener`銆?2. 鏀跺埌 task id 鍚庤鍙?snapshot銆?3. 鐢?`TaskNotificationFormatter` 鏍煎紡鍖栨秷鎭€?4. 璋冪敤 `conversationManager.addAssistantMessage` 娉ㄥ叆涓诲巻鍙层€?5. 璋冪敤 `onChange.run()` 鍒锋柊鐣岄潰銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultChatOrchestratorBackgroundTaskTest`锛屾湡鏈涘畬鎴愪换鍔″悗涓诲璇濇柊澧?`<task-notification>`銆?
## T27: 淇敼 ESC 琛屼负

**鏂囦欢锛?* `src/main/java/com/lunacode/orchestrator/ChatOrchestrator.java`銆乣DefaultChatOrchestrator.java`銆乣src/main/java/com/lunacode/tui/LanternaLunaTui.java`  
**渚濊禆锛?* T12銆乀26  
**姝ラ锛?*
1. 鍦?`ChatOrchestrator` 澧炲姞 `backgroundCurrentSubAgentOrCancel()` 榛樿鏂规硶鎴栨樉寮忔柟娉曘€?2. `DefaultChatOrchestrator` 涓紭鍏堣皟鐢?`ForegroundSubAgentTracker.adoptCurrentToBackground()`銆?3. 濡傛灉鎺ョ鎴愬姛锛岀姸鎬佹樉绀哄悗鍙颁换鍔?id锛屽苟涓嶅彇娑?token銆?4. 濡傛灉娌℃湁鍓嶅彴瀛?Agent锛屾墽琛岀幇鏈?`cancelCurrentRun()`銆?5. TUI Esc 蹇欑鏃惰皟鐢ㄦ柊鏂规硶銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultChatOrchestratorBackgroundTaskTest#escapeAdoptsForegroundSubAgent`锛屾湡鏈?ESC 鎺ョ瀛?Agent锛涙櫘閫氫富 Agent 浠嶅彲鍙栨秷銆?
## T28: 鍦ㄥ簲鐢ㄥ惎鍔ㄤ腑缁勮鏂扮粍浠?
**鏂囦欢锛?* `src/main/java/com/lunacode/app/LunaCodeApplication.java`  
**渚濊禆锛?* T7銆乀11銆乀12銆乀23銆乀25銆乀26  
**姝ラ锛?*
1. 鍒涘缓 `AgentDefinitionCatalog`锛屾潵婧愬寘鍚彃浠躲€佸唴缃€佺敤鎴枫€侀」鐩€?2. 鎵撳嵃瑙掕壊璇婃柇 warning銆?3. 鍒涘缓 `BackgroundTaskManager` 鍜?`ForegroundSubAgentTracker`銆?4. 鍒涘缓 `SubAgentService` 鍜?`AgentTool`銆?5. 鎶?`AgentTool` 娉ㄥ唽鍒?`ToolRegistry`銆?6. 鐢?`RealSubAgentHookActionExecutor` 鏇挎崲 `SubAgentPlaceholderActionExecutor`銆?7. 鎶婂悗鍙扮鐞嗗櫒鍜屽墠鍙?tracker 娉ㄥ叆 orchestrator銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=LunaCodeApplicationTest` 鎴?`mvn package -DskipTests`锛屾湡鏈涘簲鐢ㄨ閰嶇紪璇戦€氳繃锛宍Agent` 宸ュ叿娉ㄥ唽鎴愬姛銆?
## T29: 淇濇寔 Skill fork 鍏煎

**鏂囦欢锛?* `src/main/java/com/lunacode/skill/DefaultSkillForkRunner.java`銆乣src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`  
**渚濊禆锛?* T28  
**姝ラ锛?*
1. 纭鐜版湁 fork Skill 璺緞浠嶅彲鎵ц銆?2. 涓嶆妸 fork Skill 鐨勫畬鏁村瓙瀵硅瘽鍐欏叆涓诲巻鍙层€?3. 淇濇寔涓诲巻鍙插彧鍥炴祦绠€鐭€荤粨銆?4. 濡傞渶閫傞厤鏋勯€犲櫒渚濊禆锛屽彧鍋氬吋瀹规€ф敼鍔ㄣ€?
**楠岃瘉锛?* 杩愯鐜版湁 Skill 鐩稿叧娴嬭瘯锛屾湡鏈?fork Skill 琛屼负涓嶅彉銆?
## T30: 琛ュ厖瑙掕壊鍔犺浇鍗曞厓娴嬭瘯

**鏂囦欢锛?* `src/test/java/com/lunacode/subagent/FrontmatterAgentDefinitionParserTest.java`銆乣DefaultAgentDefinitionCatalogTest.java`  
**渚濊禆锛?* T6銆乀7  
**姝ラ锛?*
1. 娴嬭瘯瀹夊叏瀹℃煡绀轰緥鍙В鏋愩€?2. 娴嬭瘯 `name` 鍜?`description` 鏄犲皠鍒?`agentType` 鍜?`whenToUse`銆?3. 娴嬭瘯鍚屽悕瑕嗙洊浼樺厛绾с€?4. 娴嬭瘯鍧忓畾涔夎烦杩囧苟浜х敓 warning銆?5. 娴嬭瘯宸ュ叿寮曠敤闈炴硶鏃跺畾涔夎璺宠繃銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=FrontmatterAgentDefinitionParserTest,DefaultAgentDefinitionCatalogTest`锛屾湡鏈涘叏閮ㄩ€氳繃銆?
## T31: 琛ュ厖鍚庡彴浠诲姟鍗曞厓娴嬭瘯

**鏂囦欢锛?* `src/test/java/com/lunacode/background/DefaultBackgroundTaskManagerTest.java`銆乣DefaultForegroundSubAgentTrackerTest.java`銆乣TaskNotificationFormatterTest.java`  
**渚濊禆锛?* T10銆乀11銆乀12銆乀13  
**姝ラ锛?*
1. 娴嬭瘯 `launch` 杩斿洖鍞竴 id銆?2. 娴嬭瘯鎴愬姛瀹屾垚鐘舵€佸彉涓?`COMPLETED`銆?3. 娴嬭瘯寮傚父鐘舵€佸彉涓?`FAILED`銆?4. 娴嬭瘯 listener 鏀跺埌閫氱煡銆?5. 娴嬭瘯 `adoptRunning` 涓嶅惎鍔ㄦ柊 handle銆?6. 娴嬭瘯閫氱煡鏍煎紡鍖呭惈瀹屾暣缁撴灉銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultBackgroundTaskManagerTest,DefaultForegroundSubAgentTrackerTest,TaskNotificationFormatterTest`銆?
## T32: 琛ュ厖 AgentTool 鍗曞厓娴嬭瘯

**鏂囦欢锛?* `src/test/java/com/lunacode/tool/AgentToolTest.java`  
**渚濊禆锛?* T23銆乀24  
**姝ラ锛?*
1. 娴嬭瘯缂哄皯 task 杩斿洖鍙傛暟閿欒銆?2. 娴嬭瘯鏈紶 `subagent_type` 璧?Fork銆?3. 娴嬭瘯 `run_in_background: true` 杩斿洖 `async_launched`銆?4. 娴嬭瘯涓嶅瓨鍦?`subagent_type` 杩斿洖宸ュ叿閿欒銆?5. 娴嬭瘯鍚庡彴 Agent 璋冪敤 `Agent` 琚嫆缁濄€?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=AgentToolTest`锛屾湡鏈涘叏閮ㄩ€氳繃銆?
## T33: 琛ュ厖瀛?Agent runner 闆嗘垚娴嬭瘯

**鏂囦欢锛?* `src/test/java/com/lunacode/subagent/DefaultSubAgentRunnerFactoryTest.java`  
**渚濊禆锛?* T18銆乀19銆乀20  
**姝ラ锛?*
1. 浣跨敤 fake provider 鏋勯€犱竴娆℃棤宸ュ叿瀛?Agent 鍥炲銆?2. 楠岃瘉瀹氫箟寮忎笉澶嶅埗鐖跺巻鍙层€?3. 楠岃瘉 Fork 澶嶅埗鐖跺師濮嬪巻鍙层€?4. 楠岃瘉杈惧埌 `maxTurns` 鍚庡仠姝㈠苟璁板綍鍘熷洜銆?5. 楠岃瘉鏉冮檺纭琚洿鎺ユ嫆缁濄€?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest`锛屾湡鏈涘叏閮ㄩ€氳繃銆?
## T34: 琛ュ厖 Hook 瀛?Agent 娴嬭瘯

**鏂囦欢锛?* `src/test/java/com/lunacode/hook/RealSubAgentHookActionExecutorTest.java`  
**渚濊禆锛?* T25  
**姝ラ锛?*
1. 娴嬭瘯鏈夋晥 `sub_agent` action 鍚庡彴鍚姩浠诲姟銆?2. 娴嬭瘯涓嶅瓨鍦ㄨ鑹叉椂杩斿洖 failure銆?3. 娴嬭瘯 Hook 鎵ц鍣ㄤ笉浼氭姏鍑烘湭澶勭悊寮傚父銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=RealSubAgentHookActionExecutorTest`锛屾湡鏈涘叏閮ㄩ€氳繃銆?
## T35: 琛ュ厖 orchestrator 鍜?TUI 琛屼负娴嬭瘯

**鏂囦欢锛?* `src/test/java/com/lunacode/orchestrator/DefaultChatOrchestratorBackgroundTaskTest.java`銆乣src/test/java/com/lunacode/tui/LanternaLunaTuiTest.java`  
**渚濊禆锛?* T26銆乀27  
**姝ラ锛?*
1. 娴嬭瘯鍚庡彴浠诲姟瀹屾垚鍚庢敞鍏?`<task-notification>`銆?2. 娴嬭瘯閫氱煡涓嶈Е鍙戞柊鐨勪富 Agent 鍥炲銆?3. 娴嬭瘯 ESC 鍦ㄥ墠鍙板瓙 Agent 瀛樺湪鏃惰浆鍚庡彴銆?4. 娴嬭瘯 ESC 鍦ㄦ櫘閫氫富 Agent 蹇欑鏃朵粛鍙栨秷銆?
**楠岃瘉锛?* 杩愯 `mvn test -Dtest=DefaultChatOrchestratorBackgroundTaskTest,LanternaLunaTuiTest`銆?
## T36: 杩愯鍏ㄩ噺鍗曞厓娴嬭瘯

**鏂囦欢锛?* 鍏ㄩ」鐩? 
**渚濊禆锛?* T1-T35  
**姝ラ锛?*
1. 杩愯 `mvn test`銆?2. 璁板綍澶辫触娴嬭瘯銆?3. 淇鍥犳湰绔犳敼鍔ㄩ€犳垚鐨勫け璐ャ€?4. 閲嶈窇鐩村埌閫氳繃銆?
**楠岃瘉锛?* `mvn test` 閫€鍑虹爜涓?0銆?
## T37: 杩愯缂栬瘧鎵撳寘妫€鏌?
**鏂囦欢锛?* 鍏ㄩ」鐩? 
**渚濊禆锛?* T36  
**姝ラ锛?*
1. 杩愯 `mvn package -DskipTests`銆?2. 妫€鏌ユ槸鍚﹀瓨鍦ㄧ紪璇戦敊璇€佺己澶?import 鎴栨硾鍨嬮敊璇€?3. 淇鍚庨噸璺戙€?
**楠岃瘉锛?* `mvn package -DskipTests` 閫€鍑虹爜涓?0銆?
## T38: 鍑嗗绔埌绔祴璇曡鑹叉枃浠?
**鏂囦欢锛?* `.lunacode/agents/security-reviewer.md`  
**渚濊禆锛?* T28銆乀37  
**姝ラ锛?*
1. 鍒涘缓鎴栧鐢ㄥ畨鍏ㄥ鏌ュ瓙 Agent 绀轰緥銆?2. frontmatter 鍖呭惈 `name`銆乣description`銆乣disallowedTools`銆乣maxTurns`銆?3. 姝ｆ枃瑕佹眰鍙浠ｇ爜骞舵寜涓ラ噸绋嬪害杈撳嚭銆?4. 纭繚璇ユ枃浠跺彧鐢ㄤ簬娴嬭瘯鎴栫ず渚嬶紝涓嶈鐩栫敤鎴峰凡鏈夊唴瀹广€?
**楠岃瘉锛?* 鍚姩搴旂敤鏃惰兘鎵撳嵃鎴栧姞杞?`security-reviewer`锛屼笖娌℃湁瑙掕壊瑙ｆ瀽 warning銆?
## T39: tmux 绔埌绔祴璇曞畾涔夊紡鍚庡彴

**鏂囦欢锛?* 杩愯鐜  
**渚濊禆锛?* T38  
**姝ラ锛?*
1. 鍦?tmux 涓惎鍔?LunaCode銆?2. 杈撳叆鐪熷疄璇锋眰锛氣€滆 security-reviewer 妫€鏌ュ綋鍓嶉」鐩腑鍜屾潈闄愮浉鍏崇殑椋庨櫓锛屽悗鍙拌繍琛屻€傗€?3. 瑙傚療涓?Agent 鏄惁璋冪敤 `Agent` 宸ュ叿骞朵紶鍏?`subagent_type: security-reviewer`銆乣run_in_background: true`銆?4. 瑙傚療涓诲璇濇槸鍚︾珛鍗冲彲缁х画浜や簰銆?5. 绛夊緟鍚庡彴瀹屾垚锛岃瀵?`<task-notification>` 鏄惁鍑虹幇銆?
**楠岃瘉锛?* 鐪嬪埌 `async_launched`銆佸悗鍙颁换鍔?id锛屼互鍙婂寘鍚畬鏁寸粨鏋滅殑 `<task-notification>`銆?
## T40: tmux 绔埌绔祴璇?Fork 璺緞

**鏂囦欢锛?* 杩愯鐜  
**渚濊禆锛?* T39  
**姝ラ锛?*
1. 鍦ㄥ悓涓€ tmux 浼氳瘽涓户缁璇濄€?2. 杈撳叆鐪熷疄璇锋眰锛氣€滃紑涓€涓瓙 Agent 鎬荤粨鎴戜滑鍒氭墠璁ㄨ杩囩殑瀹炵幇椋庨櫓銆傗€?3. 瑙傚療涓?Agent 璋冪敤 `Agent` 宸ュ叿鏃朵笉浼?`subagent_type`銆?4. 纭 Fork 瀛?Agent 鐩存帴鍚庡彴杩愯銆?5. 绛夊緟缁撴灉鍥炴祦銆?
**楠岃瘉锛?* 鐪嬪埌 Fork 杩斿洖鍚庡彴浠诲姟 id锛屽畬鎴愬悗涓诲璇濇敹鍒?`<task-notification>`锛屼笖娌℃湁瀛?Agent 涓棿瀵硅瘽杞綍銆?
## T41: tmux 绔埌绔祴璇?ESC 鎵嬪姩鍒囧悗鍙?
**鏂囦欢锛?* 杩愯鐜  
**渚濊禆锛?* T39  
**姝ラ锛?*
1. 璁╀富 Agent 鍚姩涓€涓墠鍙板畾涔夊紡瀛?Agent銆?2. 瀛?Agent 杩愯鏈熼棿鎸?ESC銆?3. 瑙傚療褰撳墠鍓嶅彴瀛?Agent 鏄惁杞负鍚庡彴浠诲姟銆?4. 缁х画杈撳叆涓€鏉℃櫘閫氭秷鎭紝楠岃瘉涓诲璇濇仮澶嶄氦浜掋€?5. 绛夊緟鍚庡彴浠诲姟瀹屾垚銆?
**楠岃瘉锛?* ESC 鍚庝换鍔′笉閲嶅惎锛屼富瀵硅瘽鍙户缁緭鍏ワ紝鍚庡彴瀹屾垚鍚庨€氱煡鍥炴祦銆?
## T42: tmux 绔埌绔祴璇?Hook sub_agent

**鏂囦欢锛?* `.lunacode/config.local.yaml`銆佽繍琛岀幆澧? 
**渚濊禆锛?* T25銆乀39  
**姝ラ锛?*
1. 閰嶇疆涓€涓彲瑙﹀彂鐨?`sub_agent` Hook锛屾寚鍚?`security-reviewer`銆?2. 鍦?tmux 涓Е鍙戝搴斾簨浠躲€?3. 瑙傚療 Hook 鏃ュ織鏄惁璁板綍鐪熷疄鍚姩鍚庡彴浠诲姟銆?4. 绛夊緟鍚庡彴浠诲姟瀹屾垚銆?5. 娓呯悊娴嬭瘯鐢ㄦ湰鍦?Hook 閰嶇疆銆?
**楠岃瘉锛?* Hook 涓嶅啀杈撳嚭鏈疄鐜版棩蹇楋紝鍚庡彴浠诲姟瀹屾垚鍚庝富瀵硅瘽鏀跺埌 `<task-notification>`銆?
## T43: 瀵圭収 spec 楠屾敹鐐瑰洖褰?
**鏂囦欢锛?* `spec/12/spec.md`銆佹祴璇曡褰? 
**渚濊禆锛?* T36-T42  
**姝ラ锛?*
1. 瀵圭収 AC1-AC30 閫愰」妫€鏌ャ€?2. 鏍囪宸茬敱鍗曞厓娴嬭瘯瑕嗙洊鐨勯獙鏀剁偣銆?3. 鏍囪宸茬敱 tmux 绔埌绔鐩栫殑楠屾敹鐐广€?4. 瀵规湭瑕嗙洊椤硅ˉ娴嬭瘯鎴栬ˉ鎵嬪伐楠岃瘉銆?
**楠岃瘉锛?* AC1-AC30 鍧囨湁娴嬭瘯鎴栨墜宸ヨ瘉鎹€?
## T44: 璁板綍鏈€缁堥獙璇佽瘉鎹?
**鏂囦欢锛?* 鍚庣画楠屾敹鎶ュ憡鎴栨渶缁堝洖澶? 
**渚濊禆锛?* T43  
**姝ラ锛?*
1. 姹囨€?`mvn test` 杈撳嚭銆?2. 姹囨€?`mvn package -DskipTests` 杈撳嚭銆?3. 姹囨€?tmux 绔埌绔叧閿瀵熺粨鏋溿€?4. 璁板綍鏈仛浜嬮」浠嶆湭瀹炵幇銆?
**楠岃瘉锛?* 鏈€缁堟姤鍛婂寘鍚懡浠よ瘉鎹€佺鍒扮璇佹嵁鍜?checklist 瀵圭収缁撴灉銆?
## 鎵ц椤哄簭

```text
T1 -> T2
T3 -> T4 -> T5 -> T6 -> T7 -> T8 -> T9
T10 -> T11 -> T12 -> T13
T14 -> T15 -> T16 -> T17 -> T18 -> T19 -> T20
T21 -> T22 -> T23 -> T24
T25 -> T26 -> T27 -> T28 -> T29
T30 -> T31 -> T32 -> T33 -> T34 -> T35
T36 -> T37 -> T38 -> T39 -> T40 -> T41 -> T42 -> T43 -> T44
```
