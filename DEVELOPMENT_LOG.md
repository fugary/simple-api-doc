# 寮€鍙戣繘搴︽棩蹇?(Detailed Development Log)

鏈枃妗ｅ畬鏁磋褰曚簡 `simple-api-doc` 椤圭洰鐨勮缁嗗紑鍙戝巻绋嬨€佸姛鑳借凯浠ｅ強缁存姢璁板綍銆?

### 2026-08
- **feat**: [2026-08-07] 在项目列表卡片“状态”开关右侧新增接口与文档数量显示：1. 利用 MyBatis-Plus 的 `Wrappers.<ApiDoc>query()` 与 `listMaps` 功能在 `ApiDocServiceImpl.java` 中构建动态聚合查询（`COUNT(1)`），高性能地批量统计各项目的 API 接口数与 Markdown 文档数，避免大表实体内存拉取，同时贯彻了减少修改与无痕代码原则；在 `ApiProjectController.java` 中通过 `SimpleResult.add("counts", ...)` 批量返回至前端 `addons`；2. 前端 `ApiProjects.vue` 将统计展示的容器重构为行内标签（`inline-flex` 配合 `vertical-align`），确保与状态 Label 完美处于同一水平线；彻底替换默认的 `ElTag` 为深度定制的胶囊样式（适配明暗主题），去除斜杠分隔符采用更具呼吸感的留白（`gap: 10px`）；3. 统一并放大相关统计图标至 15px，为 API（绿色 `custom-api`）、Markdown（紫色 `custom-markdown`）及总数（蓝色 `Files`）配置对应的专属矢量图形，使得界面不仅辨识度大增，色彩也和谐统一；且当API或文档数为0时自动隐藏该项图标与数字；4. 补全 `api_cn.js` 与 `api_en.js` 国际化词条，且全量通过 ESLint 校验与 Maven 编译。



- **opt**: [2026-08-06] 浼樺寲 OpenAPI 鏁版嵁瀵煎叆鐨勭幆澧冨悎骞剁瓥鐣ヤ互鏀寔澶氭簮浜ゆ浛瀵煎叆锛?. 淇敼鍚庣鐨?`ApiDocParseUtils.java` 鐨?`mergeEnvConfigs`锛屽湪鍚屾 OpenAPI 鏂囨。鏃朵笉鍐嶈嚜鍔ㄦ竻鐞嗗洜鏉ユ簮涓嶅悓鑰屸€滄秷澶扁€濈殑鑷姩鐜锛堜粎鎸?URL 涓ユ牸鍘婚噸锛夛紝浠庤€屽畬缇庢敮鎸佺敤鎴蜂粠 SIT銆丏EMO 绛夊涓潵婧愪氦鏇垮鍏ユ暟鎹苟鎸佺画绱姞鍏ㄩ儴鍙敤鐜锛涘悓鏃跺寮轰簡鍘婚噸鏉′欢锛岃嫢涓や釜鐜鍚嶇О鐩稿悓锛堝鏃犳剰涔夌殑 `Generated server url`锛変笖涓嶄负绌猴紝鍒欎篃浼氳瑙嗕负鍚屼竴鐜骞惰鐩栵紝淇濇寔鏈€鏂板鍏ョ殑 IP锛涘湪浠ｇ爜灞傞潰鎶界浜?`isSameEnv` 鏂规硶鎻愬崌浜嗗彲璇绘€у拰浼橀泤搴︼紱2. 鍦ㄥ墠绔?`ApiEnvContentWindow.vue` 涓紑鏀句簡鍏ㄩ儴鐜閰嶇疆鐨勨€滃垹闄も€濇寜閽紙绉婚櫎 `v-if="item.manual"`锛夛紝灏嗏€滄竻鐞嗗簾寮冪幆澧冣€濈殑鎺у埗鏉冨畬鍏ㄤ氦杩樼粰鐢ㄦ埛锛屼娇寰楃敤鎴峰彲浠ヤ富鍔ㄥ垹闄ょ郴缁熶腑闀挎湡绱Н鐨勬棤鏁堟垨搴熷純鐜鍦板潃锛岃В鍐宠嚜鍔ㄧ幆澧冨洜涓鸿閿佽€屾棤娉曟竻鐞嗙殑鐥涚偣锛?. 鍦ㄥ墠绔?`ApiEnvContentWindow.vue` 鐜淇濆瓨鎿嶄綔涓紝鍦ㄥ師鏈夌殑鈥滃悕绉版煡閲嶁€濆熀纭€涓婃柊澧炰簡鈥淯RL 鏌ラ噸鈥濇牎楠岋紝鎻愬墠鎷︽埅涓庨樆姝㈢敤鎴锋墜鍔ㄦ坊鍔犱細瀵艰嚧鍚庡彴琚鐩栨垨鍒锋柊鐨勫悓鍚?鍚?URL 鐨勯厤缃€?
- **bug**: [2026-08-06] 淇澶氭瀵煎叆 API 鏂囨。鏃舵墜鍔ㄦ坊鍔犵殑鐜閰嶇疆鍑虹幇閲嶅鐨勯棶棰橈細鍦?`ApiDocParseUtils.java` 鐨?`mergeEnvConfigs` 鏂规硶涓紝鏇存柊浜嗛拡瀵?`savedEnvConfigs` 鐨勭Щ闄ら€昏緫锛岃嫢宸叉湁鐜閰嶇疆鍚屾牱瀛樺湪浜庢湰娆″嵆灏嗚娣诲姞杩涙潵鐨?`envConfigs` 涓紝鍒欑洿鎺ュ皢鍏朵粠鍘熷垪琛ㄩ噷鍓旈櫎锛堝洜涓哄悗缁?`addAll` 杩涙潵鐨勬柊鍒楄〃閲屽凡缁忓寘鍚簡缁ф壙鏃ч厤缃殑鎵嬪姩鐜锛夛紝浠庤€岄伩鍏嶅啀娆¤拷鍔犱骇鐢熼噸澶嶆潯鐩紝骞朵繚鐣欐墜鍔ㄤ慨鏀圭殑灞炴€у強姝ｇ‘鐨勫幓閲嶈涓恒€?
- **bug**: [2026-08-05] 淇瀵煎嚭鏂囨。鏃舵枃浠跺悕鍖呭惈绌烘牸锛堝 "CITSGBT - V3"锛夌敱浜?`URLEncoder.encode` 杞崲鎴?`+` 瀵艰嚧涓嬭浇鐨勬枃浠跺悕涓嚭鐜?`+-+` 鐨勯棶棰橈細鍦?`SimpleResultUtils.java` 缁勮 `Content-Disposition` header 鏃讹紝瀵?URL 缂栫爜鍚庣殑鏂囦欢鍚嶄腑鐨?`+` 鏇挎崲鍥?`%20`锛岀‘淇濈幇浠ｆ祻瑙堝櫒鑳芥纭В鐮佷负绌烘牸锛屽寮烘枃浠朵笅杞戒綋楠屻€?
- **feat**: [2026-08-05] 浼樺寲 Markdown 鏂囨。瀵煎嚭锛氬皢鎺ュ彛鎵€鍦ㄧ殑鏂囦欢澶瑰眰绾э紙濡?a/b/c锛屼笉鍖呭惈鏍圭洰褰曪級鍚堝苟浣滀负 Markdown 鐨勬爣棰樿緭鍑猴紝骞跺皢鐩稿叧鎺ュ彛鍒嗚嚦鍚勬枃浠跺す鏍囬涓嬶紝鏂逛究鐢ㄦ埛瀵艰埅涓庢祻瑙堛€?
- **opt**: [2026-08-05] 浼樺寲鎺ュ彛瀵煎嚭鏂囨。淇℃伅锛氬鏋滄帴鍙ｅ浜庡绾ф枃浠跺す涓紝灏嗘墍灞炴枃浠跺す淇℃伅绉诲叆鈥滄帴鍙ｄ俊鎭€濆尯鍩熷睍绀猴紝鍘婚櫎鍗曠嫭鍗犺鏄剧ず锛岃嫢鍙湁涓€绾ф枃浠跺す鍒欑洿鎺ヤ笉鏄剧ず锛屼娇鎺掔増鏇寸揣鍑戙€?
- **feat**: [2026-08-05] 寮€鏀惧垎浜〉闈㈢殑 Markdown 瀵煎嚭鍔熻兘锛氬湪 `ApiFolderService.js` 涓Щ闄ゅ `shareDoc` 鐨勯檺鍒讹紝浣垮緱鏂囨。鍒嗕韩椤甸潰涔熻兘鏀寔 Markdown 鏍煎紡鐨勬暣浣撴垨鍗曠瘒鎺ュ彛鏂囨。瀵煎嚭銆?
- **bug**: [2026-08-05] 淇瀵煎嚭 Markdown 鏂囨。鏃剁敱浜?schema 涓?null 瀵艰嚧 java.lang.NullPointerException 鐨勯棶棰橈細鍦?`MarkdownApiDocViewGeneratorImpl.java` 涓鍔犲 `newSchema.getSchema()` 鐨勭┖鎸囬拡鍒ゆ柇锛岄槻姝㈢敱浜?schema 鏈畾涔夊鑷存枃妗ｅ鍑哄け璐ャ€?
- **feat**: [2026-08-05] 浼樺寲宸︿晶鏍戝舰鑿滃崟鍙婇《閮ㄨ彍鍗曞浘鏍囨牱寮忥細涓?`ApiFolderTreeViewer.vue` 涓殑鍚勪釜涓婁笅鏂囪彍鍗曪紙鏂板銆佺紪杈戙€佸鍑恒€佺敓鎴愪唬鐮佺瓑锛夊強璁剧疆鑿滃崟鐨勫浘鏍囧鍔犱簡鍔ㄦ€佷富棰樿壊锛坄iconColor`锛夛紝浣跨晫闈㈠浘鏍囨洿鍔犵敓鍔ㄤ笖鍏锋湁鍖哄垎搴︼紱鍚屾椂瀹屽杽浜嗗熀纭€缁勪欢 `MoreActionsLink.vue`锛屾敮鎸侀€忎紶 `iconColor` 灞炴€э紝閫氳繃浜嗗叏闈㈢殑 ESLint 闈欐€佷唬鐮佹鏌ャ€?
- **opt**: [2026-08-04] 浠ｇ爜瀹℃煡涓庨泦涓寲绮剧畝閲嶆瀯锛?. 灏嗘爲褰㈣彍鍗曪紙`.folder-schema-tree`锛変笌鍗曢€夋爲寮圭獥锛坄.tree-single-select`锛夌殑鑺傜偣閫変腑楂樹寒鏍峰紡闆嗕腑鏀跺彛鑷?`main.css` 涓紝绉婚櫎 `TreeCheckConfig.vue` 涓粍浠剁骇鐨?`:deep` 鏍峰紡閲嶅瑕嗙洊锛屽噺灏戝啑浣欎唬鐮侊紱2. 绮剧畝 `TreeIconLabel.vue` 涓?icon 绫诲瀷鍒ゆ柇鍙婂姩鎬?class 璁＄畻灞炴€э紝淇濇寔浠ｇ爜绠€缁冨彲璇伙紱3. 纭鍏ㄩ噺浠ｇ爜绗﹀悎瑙勮寖骞堕€氳繃 ESLint 闈欐€佹鏌ャ€?
- **opt**: [2026-08-04] 浼樺寲鎺ュ彛璋冭瘯椤甸潰鍙婅璇佸脊绐椾腑鈥滅櫥褰曟帴鍙ｂ€濅笌鈥滃彉閲忊€濈殑涓嬫媺鑿滃崟浜や簰浣撻獙锛氬皢 `ApiDocLoginApiDropdown.vue` 鍜?`ApiEnvPopover.vue` 涓殑涓嬫媺瑙﹀彂鏂瑰紡鐢?`click` 缁熶竴璋冩暣涓?`hover`锛屼娇浜や簰鏇村姞骞虫粦浼橀泤锛涘悓鏃朵紭鍖栦簡 `ApiRequestFormReq.vue` 涓€滄湇鍔＄鍙戦€?娴忚鍣ㄥ彂閫佲€濋€夋嫨涓嬫媺妗嗙殑瀹藉害闄愬埗锛堢敱 `150px` 璋冩暣涓?`115px`锛夛紝鑺傜害灞忓箷绌洪棿骞朵繚璇佷腑鑻辨枃鍙岃鐜涓嬬殑姝ｅ父鏄剧ず锛涗唬鐮佸凡閫氳繃 ESLint 鏍￠獙銆?
- **opt**: [2026-08-04] 浼樺寲鍒嗕韩椤甸潰鈥滃鍒?Markdown鈥濇寜閽殑浣嶇疆鏄剧ず锛氬湪 `ApiDocViewer.vue` 涓?`MarkdownDocViewer.vue` 涓姩鎬佸垽鏂紝褰撳浜庡垎浜〉闈紙`shareDoc` 瀛樺湪锛屽嵆娌℃湁鍏ㄥ眬鍏ㄥ睆鎸夐挳锛夋椂锛屽皢鎮诞澶嶅埗鎸夐挳鐨?`bottom` 灞炴€ц嚜閫傚簲璋冩暣涓?`90px`锛岃嚜鍔ㄧЩ鍔ㄨ嚦鍘熸潵鍏ㄥ睆鎸夐挳鐨勪綅缃紝浣挎暣浣撳竷灞€鏇村姞绱у噾缇庤銆?
- **feat**: [2026-08-04] 涓烘櫘閫?Markdown 绫诲瀷鐨勬枃妗ｆ柊澧炩€滃鍒垛€濇寜閽姛鑳斤細鍦?`MarkdownDocViewer.vue` 涓坊鍔犱笌鎺ュ彛鏂囨。锛坄ApiDocViewer.vue`锛変竴鑷寸殑鍙充笅瑙掓偓娴€屽鍒?Markdown銆嶆寜閽紝澶嶇敤 `$copyText` 宸ュ叿锛屾敮鎸佹彁鍙栨枃妗ｅ悕绉颁綔涓?H1 鏍囬骞舵嫾鎺?Markdown 鍐呭涓€閿鍒跺埌鍓创鏉匡紝浼樺寲浜嗙函 Markdown 鏂囨。鐨勫鍑轰綋楠岋紱浠ｇ爜宸查€氳繃 ESLint 鏍￠獙銆?
- **opt**: [2026-08-03] 浼樺寲鍙婄簿绠€鍏ㄥ眬鐜 URL 鍚屾涓庤皟璇曞弬鏁板悎骞朵唬鐮侀€昏緫锛?. 鎶藉彇閫氱敤鐨?`NOT_SAVED_KEYS` 涓?`mergeSavedParamTarget` 灏佽宸ュ叿鏂规硶锛屾秷闄?`ApiDocViewer.vue` 涓?`ApiDocPreviewService.js` 涓?10 浣欒閲嶅鐨勫弬鏁伴澶勭悊鍚堝苟閫昏緫锛?. 绮剧畝 `ApiDocAuthorizationWindow.vue` 涓?`ApiDocViewer.vue` 涓棤鐢ㄧ殑缁勪欢灞炴€ч€忎紶锛?. 淇濊瘉涓昏鎯呴〉銆佹帴鍙ｈ皟璇曞脊绐椼€佺櫥褰曡皟璇曞脊绐楀叏閾捐矾 `targetUrl` 鐨勫搷搴斿紡鑱斿姩涓庢暟鎹崟渚嬪瓨鍌紱鍏ㄩ噺閫氳繃 ESLint 瑙勮寖鏍￠獙銆?
- **bug**: [2026-08-03] 淇 API 鏍戝舰鑿滃崟鍙抽敭寮瑰嚭涓婁笅鏂囪彍鍗曞悗鍐嶆鍙抽敭鐐瑰嚮鍏朵粬鑺傜偣鏃犲弽搴斿強鏃犳硶鏇存柊浣嶇疆鐨勯棶棰橈細鍦?`ApiFolderTreeViewer.vue` 鐨?`handleNodeContextMenu` 涓紝鍒╃敤 `showContextMenu` 鐘舵€佹帶鍒朵笂涓嬫枃鑿滃崟 DOM 閲嶆柊鎸傝浇锛岃В鍐?Element Plus 涓嬫媺缁勪欢 Popper 浣嶇疆缂撳瓨瀵艰嚧鐨勫彸閿偣鍑诲叾浠栦綅缃彍鍗曚笉绉诲姩/鏃犳硶灞曞紑鏂拌妭鐐硅彍鍗曠殑 Bug锛涘彸閿偣鍑昏妭鐐逛粎寮瑰嚭鑿滃崟涓斾笉涓诲姩鏀瑰彉褰撳墠宸查€変腑鐨勬枃妗ｆ爲鑺傜偣锛堥伩鍏嶅垏鎹㈠綋鍓嶆煡鐪嬬殑鎺ュ彛锛夛紝骞跺湪灞曞紑鑺傜偣鍙充晶 `more-actions` 鑿滃崟鏃惰嚜鍔ㄩ殣钘忓叧闂彸閿笂涓嬫枃鑿滃崟锛岄槻姝㈠脊绐楄彍鍗曢噸鍙狅紱鍏ㄩ噺閫氳繃 ESLint 瑙勮寖鏍￠獙銆?
- **opt**: [2026-08-03] 淇鍓嶇缂哄け鍙婃湭鍖归厤鐨?i18n 璧勬簮 Key锛?
  1. **琛ュ叏缂哄け Key**锛氳ˉ鍏ㄥ墠绔唬鐮佷腑寮曠敤浣嗘湭鍦?locale 涓畾涔夌殑 6 涓?Key锛歚common.msg.dataNotFound`锛?鏈壘鍒扮浉鍏虫暟鎹?锛夈€乣common.label.unlimited`锛?涓嶉檺'锛夈€乣common.msg.saveFailed`锛?淇濆瓨澶辫触銆?锛夈€乣common.msg.deleteFailed`锛?鍒犻櫎澶辫触銆?锛夈€乣common.msg.operationFailed`锛?鎿嶄綔澶辫触銆?锛夈€乣common.msg.unknownError`锛?鏈煡閿欒'锛夛紱
  2. **瀵归綈涓嫳鏂囪瑷€鍖?*锛氫慨澶嶄腑鑻辨枃璇█鍖呬笉瀵圭О闂锛岃ˉ鍏?`api.label.corsMode`锛堜腑鏂?'璺ㄥ煙妯″紡'锛夊強 `menu.label.menuManagement`锛堣嫳鏂?'Menu Management'锛夛紝骞舵竻鐞嗗啑浣欐湭寮曠敤鐨?`menu.label.menuOperation`锛?
  3. **鑷姩鍖栨牎楠?*锛氳繍琛?i18n 鏍￠獙鑴氭湰锛岀‘璁ゅ叏閮?534 涓?Key 涓嫳鏂?100% 瀵归綈涓斾唬鐮佸紩鐢ㄩ浂缂哄け锛?
- **feat**: [2026-08-03] 鍚庣绾泦涓疄鐜版枃妗ｅ垎浜ā寮忎笅銆岀櫥褰曟帴鍙ｃ€嶉厤缃嚜鍔ㄨ繃婊や笌闃叉硠闇诧細
  1. **鍚庣宸ュ叿绫诲疄鐜?*锛氬湪 `SimpleModelUtils.java` 涓柊澧?`filterGroupConfigLoginApis(String groupConfig, Set<Integer> allowedDocIds)` 鏂规硶锛屼娇鐢?Jackson 瀵?`groupConfig` JSON 鐨?`loginApiConfigs` 鏁扮粍鍩轰簬鍒嗕韩鎺堟潈 `shareDocIds` 杩涜绮惧噯杩囨护锛?
  2. **鎺у埗鍣ㄥ搷搴旇繃婊?*锛氬湪 `SimpleShareController.java` 鐨?`/shares/loadProject/{shareId}` 涓?`/shares/loadShareDoc/{shareId}/{docId}` 鎺ュ彛鍝嶅簲缁勮涓紝鑻ュ垎浜厤缃簡鍏蜂綋 `shareDocIds` 闆嗗悎锛岃嚜鍔ㄥ `groupConfig` 杩涜鎺ュ彛绾ц繃婊わ紝淇濊瘉鏈巿鏉冪殑鐧诲綍鎺ュ彛鏁版嵁缁濅笉绂诲紑鏈嶅姟鍣紱
  3. **鍓嶇闆朵慨鏀?*锛氬墠绔唬鐮佹仮澶?100% 鍘熷鐘舵€侊紝鏃犻渶浠讳綍鍓嶇鏀瑰姩锛屾寜鍘熼€昏緫瑙ｆ瀽 `groupConfig.loginApiConfigs` 鍗冲彲寰楀埌宸叉巿鏉冪殑鐧诲綍鎺ュ彛锛?
  4. 鍏ㄩ噺閫氳繃 Maven `test-compile` 鏋勫缓鏍￠獙銆?

- **opt**: [2026-08-04] 娣卞害瀹℃煡骞剁簿绠€鍓嶅悗绔垎浜厤缃悎骞朵笌杩囨护浠ｇ爜閫昏緫锛屾秷闄や笉蹇呰鐨勯噸澶嶄慨鏀癸細1. 閽堝鍓嶇 `SimpleShareApi.js` 涓鏉傜殑 `mergeEnvConfigs` 閫昏緫锛屽洜鍚庣 `SimpleShareController.java` 宸插埄鐢?`SimpleModelUtils.filterShareProject` 缁熶竴瀹屾垚浜嗙幆澧冮厤缃紙envContent锛変笌鐧诲綍鎺ュ彛锛坙oginApiConfigs锛夌殑瀹夊叏杩囨护锛屽墠绔棤闇€杩涜浜屾寰幆浜ゅ弶姣斿锛屽皢鍏跺ぇ骞呭害绠€鍖栵紝鐩存帴淇′换鍚庣宸茶繃婊ゆ暟鎹紝褰诲簳娑堥櫎鍓嶅悗绔€昏緫閲嶅彔涓庡啑浣欎唬鐮侊紱2. 瀹℃煡 `ApiDocPreviewService.js` 涓?`paramTarget` 鏁版嵁瀵硅薄鐨勬瀯寤洪€昏緫锛岀‘淇濆畠濮嬬粓淇濇寔杞婚噺鍖栵紝缁濅笉寮曞叆 `apiShare` 绛夊叏閲忓垎浜厤缃ぇ瀵硅薄锛屼繚闅滃唴瀛樺畨鍏ㄤ笌浜や簰娴佺晠锛?. 鍚庣闆嗕腑澶勭悊 JSON 瑙ｆ瀽杞崲锛堝 `filterShareEnvContent`锛夛紝鎵€鏈夋牳蹇冨垎浜壌鏉冨強鏁忔劅鏁版嵁鎴柇缁熶竴鏀跺彛浜?Controller 杈撳嚭鍓嶏紝浣挎灦鏋勬洿鍔犳竻鏅颁紭闆咃紱閫氳繃鍓嶇 ESLint 涓庡悗绔?`test-compile` 鍏ㄩ噺妫€楠屻€?

### 2026-07
- **opt**: [2026-07-31] 娣卞害浠ｇ爜瀹℃煡涓?Vue 缁勪欢绾ч噸鏋勪紭鍖栵細1. 鍦?`ApiEnvParams.vue` 涓彂鐜板苟鎶藉彇楂樺害閲嶅鐨?`<TreeConfigWindow>` 鏍戝舰寮圭獥 DOM 缁撴瀯锛屽皢鍏剁粺涓€鏀跺彛涓哄崟涓€寮圭獥瀹炰緥锛岄€氳繃 `treeConfigContext` 鐘舵€佷笌鍔ㄦ€?`:single-select`銆乣:title` 灞炴€у姩鎬佸鐢ㄤ簬鈥滅櫥褰曟帴鍙ｉ€夋嫨鈥濅笌鈥滄彁鍙栬鍒欐帴鍙ｉ€夋嫨鈥濅袱澶у満鏅紝鎴愬姛绉婚櫎杩?40 琛岄噸澶嶅啑浣欑殑 template 浠ｇ爜锛?. 灏嗛噸澶嶇殑 `groupConfig` JSON 鍙嶅簭鍒楀寲閫昏緫鎶借薄鎻愬彇涓虹嫭绔嬬殑 `parseGroupConfig` 宸ュ叿鍑芥暟锛屽噺灏戜笟鍔￠€昏緫涓殑蹇冩櫤璐熸媴涓庨噸澶嶄唬鐮侊紝澶у箙鎻愬崌浠ｇ爜浼橀泤搴︺€佸彲璇绘€т笌鍙淮鎶ゆ€э紱3. 閫氳繃 ESLint 闈欐€佷唬鐮佹鏌ャ€?
- **opt**: [2026-07-31] 浼樺寲鍏ㄥ眬鍙橀噺鎻愬彇瑙勫垯琛ㄥ崟鏍￠獙鏍瑰洜淇銆佷竴閿竻绌轰笌鏍戦€夋嫨鍚屾锛?. 褰诲簳瑙ｅ喅淇濆瓨鎶ラ敊闃绘柇锛堟渶缁堟牴鍥犱慨澶嶏級锛氬畾浣嶅埌 Element Plus `el-table` 鍦ㄦ覆鏌撳竷灞€涓庡垪瀹藉害璁＄畻鏃讹紝浼氫娇鐢ㄧ┖瀵硅薄 `{}` 娓叉煋铏氭嫙娴嬮噺琛岋紝璋冪敤 `item.extractRules.indexOf({})` 蹇呯劧杩斿洖 `-1`锛屽鑷?`<el-form-item>` 璇皢 `envParams.0.extractRules.-1.apiPath` 娉ㄥ唽杩?`el-form` 鏍￠獙琛ㄥ苟瑙﹀彂 `fieldValue: undefined` 鎶ラ敊闃绘柇淇濆瓨锛涢€氳繃鍦?`matchPathSlot` 鐨?`<el-form-item>` 涓婃坊鍔?`v-if="item.extractRules && item.extractRules.indexOf(rule) !== -1"` 鏉′欢杩囨护锛岀‘淇濅粎鐪熷疄鏁版嵁琛屾覆鏌撴牎楠岄」锛屽交搴曟牴闄や簡璇ラ棶棰橈紱2. 瑙ｅ喅娓呯┖闇€瑕佺偣鍑讳袱娆＄殑闂锛氬湪 `switchToCustomPath` 娓呯┖閫昏緫涓悓姝ラ噸缃?`rule.apiPath = ''`锛屽疄鐜颁竴閿悓鏃舵竻绌哄凡缁戝畾鎺ュ彛涓庤矾寰勬枃鏈紱3. 瑙ｅ喅娓呯┖鍚庨噸鏂版墦寮€閫夋嫨鏍戞畫鐣欓珮浜妭鐐归棶棰橈細鍦?`TreeCheckConfig.vue` 涓负 `selectedKeys` 娣诲姞娣卞害 `watch` 鐩戝惉锛屽綋缁戝畾鐨勫凡閫?Array 涓虹┖鏃惰嚜鍔ㄨЕ鍙?`treeRef.value.setCurrentKey(null)`锛屼繚璇佸脊绐楅珮浜姸鎬佷笌搴曞眰缁戝畾鏁版嵁 100% 鍚屾锛?. 閫氳繃鍏ㄩ噺 ESLint 瑙勮寖鏍￠獙銆?
- **feat**: [2026-07-31] 鍏ㄥ眬鍙橀噺涓庢彁鍙栭厤缃敮鎸侀€夋嫨椤圭洰鎺ュ彛鍖归厤锛?. 鍗囩骇 `ApiEnvParams.vue` 涓殑鍙橀噺鎻愬彇瑙勫垯閰嶇疆锛屽湪鈥滃尮閰嶈矾寰勨€濅笌鈥滄鍒欏尮閰嶁€濆垪寮曞叆妯″紡鍒囨崲涓庢爲鐘堕€夋嫨鍣紝鏀寔鐩存帴閫氳繃 900px `TreeConfigWindow` 閫夋嫨褰撳墠椤圭洰鐨勫叿浣撴帴鍙ｈ妭鐐癸紝鑷姩甯﹀嚭 HTTP Method Tag锛堝 POST/GET锛夈€佹帴鍙ｅ悕绉颁笌 URL 璺緞锛涘悓鏃舵彁渚涒€滆嚜瀹氫箟璺緞鈥濅笌鈥滄洿鎹㈡帴鍙ｂ€濇寜閽紝淇濇寔瀵规ā绯婅矾寰勪笌姝ｅ垯鍖归厤鐨勫畬鍏ㄥ吋瀹癸紱2. 鍗囩骇 `ApiDocPreviewService.js` 涓殑 `isPathMatch` 涓?`extractVariables` 鍖归厤寮曟搸锛屽鍔?`currentApiId` 鍙傛暟涓?`rule.apiId` 鍒ゅ畾锛屼娇宸茬粦瀹氭帴鍙ｇ殑鎻愬彇瑙勫垯鍦ㄦ帴鍙?URL 淇敼鍚庝緷鐒剁簿鍑嗙敓鏁堬紱3. 鍦?`ApiDocRequestPreview.vue` 鍙戦€佽皟璇曡姹傛椂浼犲叆褰撳墠璋冭瘯鎺ュ彛 ID (`apiDocDetail.value.id`)锛?. 琛ュ厖 `api_cn.js` 涓?`api_en.js` 鍥介檯鍖栨枃妗堬紝閫氳繃鍏ㄩ噺 ESLint 瑙勮寖楠岃瘉銆?
- **bug**: [2026-07-31] 淇 API 鏍戝舰鑿滃崟鍙抽敭寮瑰嚭涓婁笅鏂囪彍鍗曞悗鍐嶆鍙抽敭鐐瑰嚮鍏朵粬鑺傜偣鏃犲弽搴旂殑闂锛氬湪 `ApiFolderTreeViewer.vue` 鐨?`handleNodeContextMenu` 涓紝鍦ㄦ洿鏂板脊鍑轰綅缃強灞曞紑鏂拌彍鍗曞墠鏄惧紡璋冪敤 `contextMenuDropdownRef.value?.handleClose?.()` 鍏抽棴宸叉湁鑿滃崟锛岀‘淇?Element Plus 涓嬫媺缁勪欢閲嶇疆鐘舵€佸苟鍦ㄦ柊鑺傜偣浣嶇疆閲嶆柊瀹氫綅寮瑰嚭锛涘叏閲忛€氳繃 ESLint 鏍￠獙銆?
- **feat**: [2026-07-31] 鍗囩骇璇锋眰璋冭瘯涓庤璇佸脊绐椾腑鐨勭櫥褰曟帴鍙ｄ氦浜掞細1. 鎶藉彇 `openLoginApiDebug` 涓?`parseLoginApiConfigs` 閫氱敤鏈嶅姟鏂规硶鑷?`ApiDocPreviewService.js`锛屾秷闄や唬鐮侀噸澶嶏紱2. 鎻愬彇鍏叡缁勪欢 `ApiDocLoginApiDropdown.vue` 灏佽鈥滅櫥褰曟帴鍙ｂ€濆閫変笅鎷?鍗曢€夐摼鎺ラ€昏緫涓?`<ApiMethodTag>`銆佹帴鍙ｅ悕绉般€乁RL 璺緞鐨勫瘜鏂囨湰鍛堢幇锛屽交搴曟秷闄ゅ師鏈変唬鐮佷腑鐨勯噸澶?DOM 缁撴瀯锛?. 鍗囩骇鎺ュ彛璋冭瘯璇锋眰鏍忥紙`ApiRequestFormReq.vue`锛夌殑鈥滅櫥褰曟帴鍙ｂ€濇帶浠讹紝鏃犵紳澶嶇敤涓婅堪绮剧畝缁勪欢锛?. 鍦ㄨ璇佸脊绐楋紙`ApiDocAuthorizationWindow.vue`锛夐《鏍忓彸渚ф柊澧炩€滅櫥褰曟帴鍙ｂ€濆揩鎹峰叆鍙ｏ紝鍦ㄩ厤缃湁鐧诲綍鎺ュ彛鏃跺厑璁哥洿鎺ヨЕ鍙戠櫥褰曪紝蹇€熻幏鍙栦笌鍒锋柊 Token锛?. 閫氳繃 ESLint 浠ｇ爜瑙勮寖鍏ㄩ噺楠岃瘉锛屼繚璇佷唬鐮佹瀬鑷寸簿绠€涓庨珮浼橀泤搴︺€?
- **feat**: [2026-07-31] 鍏ㄥ眬鍙橀噺涓庢彁鍙栭厤缃柊澧炲彉閲忔彁鍙栬鍒欏強鐧诲綍鎺ュ彛澶氶€夋嫋鎷芥帓搴忓姛鑳斤細1. 鍗囩骇閫氱敤琛ㄦ牸琛ㄥ崟缁勪欢 `CommonTableForm`锛圼index.vue](file:///d:/MyCodes/OtherCodes/simple-api-doc-parent/simple-api-doc-ui/src/components/common-table-form/index.vue)锛夛紝鏂板 `sortable` 灞炴€ф敮鎸侊紱涓鸿〃鏍肩粦瀹?`useRenderKey()` 鐢熸垚鐨勫敮涓€ `:row-key="rowKey"`锛岃В鍐冲洜涓虹己灏戠ǔ瀹氳 Key 瀵艰嚧 `el-table` 鍦?Sortable.js 鎷栨嫿瀹屾垚鍚庢寜榛樿 index 閲嶆柊娓叉煋鑰屼骇鐢熺殑鈥滄澗鎵嬭瑙夊脊鍥炩€濋棶棰橈紱涓烘嫋鎷藉浘鏍囧垪娣诲姞涓庤〃鍗曢」瀹屽叏涓€鑷寸殑 32px 寮规€ч珮搴﹀鍣紝瑙ｅ喅鍥犱负琛ㄥ崟椤归鐣欓敊璇彁绀虹┖闂村鑷存嫋鎷藉浘鏍囨棤娉曚笌鍙充晶杈撳叆妗嗘按骞冲眳涓榻愮殑闂锛?. 鍗囩骇 `useSortableParams` Hook锛圼CommonHooks.js](file:///d:/MyCodes/OtherCodes/simple-api-doc-parent/simple-api-doc-ui/src/hooks/CommonHooks.js)锛夛紝鑷姩璇嗗埆骞跺吋瀹?`el-table` 鐨?`tbody` 鑺傜偣锛屼娇琛ㄦ牸琛屾敮鎸?Sortable.js 鎷栨嫿鎺掑簭锛?. 鍦?`ApiEnvParams.vue` 涓负鍙橀噺閰嶇疆鐨勨€滄彁鍙栬鍒欌€濊〃鏍煎紑鍚?`:sortable="true"`锛屾敮鎸佹彁鍙栬鍒欐寜闇€涓婁笅鎷栨嫿璋冩暣瑙﹀彂椤哄簭锛?. 鍦?`ApiEnvParams.vue` 涓负鈥滅櫥褰曟帴鍙ｉ厤缃€濆閫夊垪琛ㄩ泦鎴愭嫋鎷芥帓搴忎笌鎮诞鎷栨嫿鎵嬫焺锛屽苟浼樺寲 `handleTreeSelectSubmit`锛屽湪閲嶆柊鎵撳紑閫夋嫨鏍戝鍒犳帴鍙ｆ椂淇濈暀宸查厤缃帴鍙ｇ殑鑷畾涔夋帓搴忥紱5. 浼樺寲 `CommonParamsEdit.vue` 涓?`ApiEnvContentWindow.vue` 涓嫋鎷芥墜鏌勭殑 CSS 杈硅窛锛屼娇鍏朵笌杈撳叆鎺т欢淇濇寔浼橀泤瀵归綈锛?. 閫氳繃 ESLint 浠ｇ爜瑙勮寖鍏ㄩ噺楠岃瘉銆?
- **feat**: [2026-07-31] 閲嶆瀯鍏ㄥ眬鍙橀噺涓庢彁鍙栭厤缃腑鐨勭櫥褰曟帴鍙ｉ€夋嫨涓庢樉绀轰氦浜掞細1. 瑙ｅ喅閫変腑鐧诲綍鎺ュ彛鏈睍绀?Method 绛変俊鎭殑闂锛屽皢宸查€夌櫥褰曟帴鍙ｅ憟鐜颁负绠€缁冭鍗＄墖锛屾竻鏅板睍绀?`<ApiMethodTag>`锛堝 POST/GET 绛夊尯鍒鑹茬殑鏂规硶鏍囩锛夈€佹帴鍙ｅ悕绉板強 URL 璺緞锛?. 搴熷純涓嬫媺妗嗗唴宓屽娓叉煋澶у瀷 API 鏍戯紙`el-tree-select`锛夌殑鍋氭硶锛屾敼鐢?900px 澶х獥鍙?`TreeConfigWindow` 鏍戝舰閫夋嫨鍣紝褰诲簳瑙ｅ喅涓嬫媺妗嗘悳绱㈠崱椤夸笌 DOM 娓叉煋鎬ц兘鐡堕锛?. 鎻愬彇 `buildLoginApiConfig` 杞崲鍑芥暟锛屾秷闄ら噸鏋勮繃绋嬩腑鐨勯噸澶嶅璞℃瀯寤轰唬鐮侊紝绉婚櫎 50 澶氳闈炲繀瑕佽嚜瀹氫箟 CSS 绫伙紝鍏ㄩ噺浣跨敤椤圭洰鏍囧噯 Element Plus 鍙橀噺涓庡唴鑱?Flex 寮规€у竷灞€锛屼繚璇佷唬鐮佹瀬鑷寸簿绠€涓庨珮鍙鎬э紱4. 閫氳繃 ESLint 浠ｇ爜瑙勮寖鍏ㄩ噺楠岃瘉銆?
- **opt**: [2026-07-31] 浼樺寲瀵煎嚭鍜岀敓鎴愪唬鐮佺瓑寮圭獥涓殑 API 鏂囨。鏍戝舰鑺傜偣鏄剧ず妯″紡锛?. 绉婚櫎 `toShowTreeConfigWindow` 涓?`toShowCodeGenConfigWindow` 瀵煎嚭/鐢熸垚浠ｇ爜寮圭獥鐢熸垚鑺傜偣鏍戞椂寮哄埗浼犲叆 `defaultShowLabel: sharePreference.defaultShowLabel` 鐨勯€昏緫锛岄槻姝㈠湪涓荤晫闈晶杈规爮閰嶇疆涓衡€滄樉绀?URL鈥濇椂瀵煎嚭/鐢熸垚浠ｇ爜鏍戣妭鐐瑰悕绉拌璇垽鏇挎崲涓虹函 URL 璺緞锛?. 鍙傝€冪櫥褰曟帴鍙ｉ厤缃爲锛坄ApiEnvParams.vue`锛夌殑宸﹀彸鍙屼晶娓叉煋妯″紡锛屽湪 `TreeIconLabel.vue` 涓ˉ鍏?`.el-tree-node__label` 鐨?flex 寮规€х洅鎷変几灞炴€у苟鏂板 `url` 灞炴€э紝鍦ㄥ熀纭€缁勪欢涓粺涓€灏佽宸﹀彸鍙屼晶鍒嗘爮鏄剧ず锛?. 鍗囩骇 `ApiDocExportWindow.vue`銆乣ApiDocCodeGenWindow.vue` 涓?`ApiProjectShares.vue` 鐨勬爲鑺傜偣妯℃澘锛屽乏渚ф竻鏅板睍绀鸿姹?Method 鏍囩锛堝 POST/GET锛変笌鎺ュ彛/鏂囦欢澶瑰悕绉帮紙`docName`锛夛紝鍙充晶浠ユ瑕侀鑹诧紙`--el-text-color-secondary`锛夌簿鍑嗗彸瀵归綈灞曠ず鎺ュ彛 URL 璺緞锛屽悓鏃朵繚璇佷袱鑰呭悕绉颁笌璺緞瀹屾暣鍙锛?. 缁熶竴灏嗗鍑恒€佺敓鎴愪唬鐮佸強鍏ㄥ眬鍙橀噺閰嶇疆绛夋爲寮圭獥瀹藉害璋冩暣涓洪€備腑鐨?`900px`锛屽吋椤惧ぇ灞忔樉绀哄畬鏁村害涓庡脊绐楃揣鍑戝害锛?. 绉婚櫎鍒嗕韩閰嶇疆寮圭獥涓凡鍐椾綑搴熷純鐨勩€屾帴鍙ｆ樉绀轰负鍚嶇О/鏄剧ず涓篣RL銆嶅垏鎹㈡寜閽紙`customToggleButtons` / `useCustomDocLabel`锛夛紝褰诲簳瑙ｅ喅宸﹀彸閲嶅鏄剧ず鍚屼竴 URL 鐨勯棶棰橈紱6. 閫氳繃 ESLint 浠ｇ爜瑙勮寖鍏ㄩ噺楠岃瘉銆?
- **feat**: [2026-07-30] 鏂板鐧诲綍鎺ュ彛閰嶇疆涓庤皟璇曞伐鍏锋爮蹇嵎寮圭獥鍔熻兘鍙婄簿绠€閲嶆瀯锛?. 鍦ㄣ€愬叏灞€鍙橀噺涓庢彁鍙栭厤缃€戯紙`ApiEnvParams.vue`锛夊脊绐椾腑澧炲姞銆愮櫥褰曟帴鍙ｉ厤缃€戦〉绛撅紝浣跨敤 `el-tree-select` 涓?`calcProjectItem` 鏋勫缓鏍囧噯鐨勯」鐩枃浠跺す涓?API 鎺ュ彛鏍戝舰閫夋嫨鍣紝鍙厑璁搁€夋嫨鎺ュ彛鑺傜偣骞跺瓨鑷?`groupConfig.loginApiConfig`锛涗娇鐢?`cloneDeep` 浼犻€掓暟鎹伩鍏?`calcProjectItem` 浜х敓 `parent` 寰幆寮曠敤瀵艰嚧鐨?`Converting circular structure to JSON` 淇濆瓨鎶ラ敊锛涘悎骞跺脊绐楁暟鎹姞杞戒负鍗曚竴缃戠粶璇锋眰锛屽噺灏戝啑浣欏紑閿€锛?. 淇鐐瑰紑鐧诲綍鎺ュ彛璋冭瘯寮圭獥鍐呭涓虹┖鐨勯棶棰橈紝鐧诲綍鎺ュ彛寮圭獥閲囩敤鐙珛 Top 绾?Modal 娴眰寮圭獥锛坄ApiRequestPreviewWindow`锛夛紝涓嶈鐩栧簳灞傚凡鍦ㄨ皟璇曠殑椤甸潰鎴栫獥鍙ｏ紱涓荤晫闈㈡帴鍙ｇ紪杈戠殑鏁版嵁鐢?Pinia `shareParamTargets` 瀹炴椂鎸佷箙鍖栵紙`watch(paramTarget)` 瀹炴椂鐩戝惉锛夛紝鐧诲綍寮圭獥鍏抽棴鍚庡簳灞傚凡鏈夎皟璇曟暟鎹浂涓㈠け锛涘鎺?Pinia `shareParamTargets` 缂撳瓨鎭㈠鏈哄埗锛坄preHandler` / `changeHandler` & `copyParamsDynamicOption`锛夛紝鑷姩鍚屾杩樺師涓庡疄鏃舵寔涔呭寲鐢ㄦ埛涔嬪墠鍦ㄧ櫥褰曢〉闈㈠～鍐欑殑璇锋眰鍙傛暟锛堝璐﹀彿/瀵嗙爜/Headers/URL锛夛紱鍦?`ApiDocRequestPreview.vue` 涓ˉ鍏?`handConfig` 鍙€夐槻寰★紝淇绌烘寚閽堟姤閿欙紱3. 绉婚櫎鐙珛寮圭獥涓笉闇€瑕佺殑椤甸潰宓屽叆/鏂扮獥鍙ｆ墦寮€鎸夐挳 (`OpenInNewFilled` 閾炬帴)锛?. 杩樺師闈炵浉鍏虫枃浠舵敼鍔紝鍏ㄩ噺閫氳繃 ESLint 瑙勫垯鏍￠獙銆?
- **opt**: [2026-07-30] 浼樺寲API閿欒鏃ュ織涓庢彁绀哄睍绀猴紙绠€鍖栫増锛夛細閫氳繃鎶涘嚭涓庢崟鑾?`SimpleRuntimeException` 鏇夸唬澶ц寖鍥寸殑鎺ュ彛鍙婃湇鍔″眰绛惧悕淇敼銆傚湪 `SimpleHttpClientUtils` 鎶涘嚭 HTTP 寮傚父锛屽湪 `UrlDocContentProviderImpl` 涓?`ApiProjectServiceImpl` 杩涜缁熶竴鎹曡幏杞崲涓哄惈璇︾粏鍘熷洜鐨?`SimpleResult` 杩斿洖锛屼繚鎸佸簳灞傛帴鍙ｏ紙濡?`ApiDocImporter`锛夌殑浠ｇ爜绠€娲佷笌绾磥锛屽噺灏戝ぇ閲忔枃浠剁殑鏀瑰姩骞舵彁鍗囦唬鐮佸彲璇绘€с€?
- **opt**: [2026-07-30] 浼樺寲 API 鏂囨。瀵煎叆鍙婁换鍔℃墽琛岃繃绋嬩腑鐨勯敊璇師鍥犲睍绀轰笌鍙岄噸鏃ュ織璁板綍鏈哄埗锛?. 鍚庣 `SimpleHttpClientUtils.java` 澧炲己 GET 璇锋眰寮傚父鏃ュ織杈撳嚭 `logger.error("鎵цGET璇锋眰閿欒: url={}", url, e)` 骞舵姏鍑哄紓甯镐繚鐣欐牴鍥狅紱2. `UrlDocContentProviderImpl.java` 鍦ㄧ綉缁滃紓甯告垨闈?200 HTTP 鍝嶅簲锛堝 404/500/401锛夋椂璁板綍甯?URL 涓?HTTP 鐘舵€佽鐨勭郴缁?ERROR 鏃ュ織锛屽苟杩斿洖鍚缁嗗師鍥犵殑 `SimpleResult`锛堝 `"URL鏁版嵁涓嬭浇澶辫触: HTTP/1.1 404 Not Found"`锛夛紱3. `ApiDocImporter.java` 涓?`SwaggerImporterImpl.java` 澧炲姞 `parseImport` 鏂规硶锛屽湪 OpenAPI 瑙ｆ瀽澶辫触鏃惰褰曞寘鍚?`SwaggerParseResult.getMessages()` 鐨?ERROR 鏃ュ織骞惰繑鍥炴嫾鎺ョ殑璇︾粏閿欒鎻愮ず锛?. `ApiProjectServiceImpl.java` 涓?`ApiProjectImportController.java` 淇濈暀瀹屾暣閿欒娑堟伅閫忎紶鑷冲墠绔紱5. 淇 `ProjectAutoImportInvoker.java` 瀵煎叆鏇存柊澶辫触鏃ュ織璇紩鐢ㄥ彉閲忕殑 Bug锛岀‘淇濆簳灞傝缁嗛敊璇師鍥犲畬鏁村瓨鍏?`ApiLog` 鏁版嵁搴撹〃涓紝渚夸簬鍦ㄧ郴缁熲€滄棩蹇楃鐞嗏€濋〉闈腑绮惧噯妫€绱笌杩借釜銆?
- **feat**: [2026-07-29] 澧炲己鏂囨。妯″紡锛圡arkdown 妯″紡锛変笅鐨勪俊鎭畬鏁存€т笌澶嶅埗浣撻獙锛?. 鍚庣 `MarkdownApiDocViewGeneratorImpl.java` 涓?`ApiDocFreemarkerUtils.java` 鏀寔瑙ｆ瀽鎺ュ彛涓庨」鐩厤缃殑 `securityRequirements` 鍙?`securitySchemas` 璁よ瘉瑙勫垯锛屽苟鍦?`ApiDocMdView.md.ftl` 妯℃澘涓柊澧?`### 璁よ瘉` / `### Authorization` 绔犺妭娓叉煋杈撳嚭锛堜繚鎸佸悗绔墖娈电嫭绔嬶紝淇濋殰椤圭洰澶ф枃妗ｅ鍑烘椂鐨勬爣棰樺眰绾ц鑼冿級锛涗慨姝?`messages_zh_CN.properties` 涓?`messages.properties` 涓殑 `api.label.authorization` 璇嶆潯涓?`璁よ瘉`锛?. 鍓嶇 `api_cn.js` 涓?`api_en.js` 鏂板 `api.label.copyMarkdown` 鍥介檯鍖栬瘝鏉★紱3. 鍓嶇鍦?`main.css` 涓彁鍙栧鐢ㄥ叏灞€閫氱敤鎮诞鎺т欢绫?`.floating-action-btn`锛堝寘鍚榻愬叏灞忔寜閽殑闃村奖銆佽竟妗嗐€佽繃娓¤壊涓?16px 绮惧噯鍥炬爣姣斾緥锛夛紱鍦?`ApiDocViewer.vue` 涓鍏?`$copyText` 骞朵簬鈥滄枃妗ｆā寮忊€濅笅鍙充笅瑙掓偓娴伐鍏锋爤锛坄bottom: 140px; right: 40px`锛夋覆鏌撲竴鑷撮鏍肩殑銆屽鍒?Markdown銆嶆寜閽紝骞跺湪鐐瑰嚮澶勭悊鍑芥暟 `handleCopyMarkdown` 涓墠绔笓灞炲姩鎬佹嫾鎺?`# 鎺ュ彛鍚嶇О` H1 澶ф爣棰橈紝鏀寔涓€閿皢瀹屾暣 Markdown 鍘熸枃澶嶅埗鑷冲壀璐存澘锛屽苟閫氳繃 ESLint 浠ｇ爜瑙勮寖楠岃瘉銆?
- **opt**: [2026-07-29] 浼樺寲鏂囨。妯″紡锛圡arkdown 妯″紡锛変笅鐨勬暟鎹ā鍨嬫帓搴忥細鍦?`MarkdownApiDocViewGeneratorImpl.java` 涓柊澧?`sortSchemasMap` 鏅鸿兘閲嶆帓閫昏緫锛屾寜銆愯姹備富妯″瀷 -> 鍝嶅簲涓绘ā鍨?-> 鍏宠仈宓屽妯″瀷 -> 鍏朵粬閫氱敤妯″瀷銆戝洓灞備弗鏍间紭鍏堢骇閲嶆柊缁勭粐 `schemasMap`锛屼娇鏂囨。妯″紡涓嬪簳閮ㄧ殑鈥滄暟鎹ā鍨嬧€濆垪琛ㄥ強鍙充晶澶х翰锛坄md-catalog`锛夋渶浼樺厛缃《鏄剧ず褰撳墠鎺ュ彛鐨勮姹備綋妯″瀷涓庡搷搴斾綋妯″瀷锛涙柊澧?`MarkdownApiDocViewGeneratorImplTest` 鍗曞厓娴嬭瘯骞堕€氳繃楠岃瘉銆?
- **bug**: [2026-07-29] 淇浠庨」鐩鎯呰繘鍏ュ鍏ヤ换鍔″垪琛ㄤ笌鍒嗕韩鍒楄〃鏃堕」鐩垎缁勫悕绉版樉绀轰负鍘熷 `groupCode` ID 涓茬殑闂锛氬湪 `ApiProjectTasks.vue` 鍜?`ApiProjectShares.vue` 鐨?`initLoadOnce` 涓紝褰?`inProject` 涓?`true` 鏃惰ˉ鍏?`loadGroupsAndRefreshOptions()` 璋冪敤锛岀‘淇濆垵濮嬪寲鍗冲姞杞藉綋鍓嶇敤鎴风殑椤圭洰鍒嗙粍鏁版嵁锛屼娇琛ㄦ牸娓叉煋椤圭洰鍒楁椂鑳芥纭尮閰嶅苟鏄剧ず鐪熷疄鐨勯」鐩垎缁勫悕绉般€?
- **opt**: [2026-07-29] 浼樺寲鏁版嵁妯″瀷/Schema 缂栬緫鏍戞坊鍔犲睘鎬т氦浜掍綋楠岋細1. 鍦?`ApiComponentSchemaEditTree.vue` 鐨?`addPropertyInline` 鏂规硶涓鍔犵埗鑺傜偣鑷姩灞曞紑閫昏緫锛坄node.expanded = true`锛夛紱2. 瑙ｅ喅鍥犱负 `el-tree` 寮€鍚?`lazy` 鎳掑姞杞藉強鍒锋柊閲嶆柊鎸傝浇 DOM 瀵艰嚧 `nextTick` 鏃犳硶鍗虫椂鎹曟崏 DOM 鐨勯棶棰橈紝鏂板甯﹂噸璇曟満鍒剁殑 `scrollToAndFocusNewRow` 寮傛杞瀹氫綅锛?. 鑷姩瑙﹀彂灞呬腑骞虫粦婊氬姩锛坄scrollIntoView({ behavior: 'smooth', block: 'center' })`锛夛紝纭繚褰撳璞″睘鎬ц緝澶氭椂鏂板灞炴€ц〃鍗曞嵆鏃跺钩婊戝眳涓睍绀哄湪褰撳墠瑙嗗彛鍐咃紱4. 鑷姩瀹氫綅骞惰仛鐒︹€滃睘鎬у悕绉扳€濊緭鍏ユ锛坄input.focus()`锛夛紝骞跺彔鍔?2s 鏌斿拰娓愬彉鑴夊啿楂樹寒鍔ㄧ敾锛坄@keyframes property-highlight`锛夈€?
- **feat**: [2026-07-29] 鏂板 AI 鏅鸿兘鐢熸垚鏁版嵁妯″瀷鍔熻兘锛?. 鍚庣鍦?`AiCacheController` 涓柊澧?`/admin/ai/caches/generate-model` 鎺ュ彛锛屽熀浜庝笟鍔￠渶姹傛弿杩扮敓鎴?PascalCase 鏍煎紡鐨勬ā鍨嬪悕绉般€佷笟鍔℃弿杩板強绗﹀悎 OpenAPI 3.0 / JSON Schema 瑙勮寖鐨勬爣鍑?Schema 缁撴瀯锛?. 鍓嶇鍦?`AiCacheApi.js` 澧炲姞 `generateModel` API 鎺ュ彛锛屽苟鍦ㄦ暟鎹ā鍨嬭〃鍗曪紙`ApiProjectComponent.vue`锛夆€滀繚瀛樷€濇寜閽梺杈规柊澧炵畝娲佹棤鍥炬爣鐨勩€宍AI鐢熸垚`銆嶆寜閽紝淇濇寔涓庝繚瀛樸€佸垹闄ゃ€佸鍒剁瓑鎸夐挳澶栬涓€鑷达紱3. 鎻愪緵闇€姹傛弿杩拌緭鍏ュ脊绐楋紙淇 `placeholder` 浼犻€掍綅缃紝浣垮崰浣嶆枃鏈甯告樉绀猴級锛屾敮鎸佽嚜鐢遍€夋嫨 AI 閰嶇疆锛屽苟鍦?`AiCacheList.vue` 涓ˉ鍏?`generate_model` 绫诲瀷鐨勮〃鏍兼牸寮忓寲涓庣瓫閫夐」锛?. 鏍￠獙骞惰鐩栧叏閮ㄦ柊澧炲浗闄呭寲璇嶆潯锛屽苟閫氳繃 ESLint 浠ｇ爜瑙勮寖楠岃瘉銆?

- **opt**: [2026-07-29] 褰诲簳娓呯悊搴熷純绌哄嚱鏁?`syncCachedParamsToTarget`锛氫粠 `ApiDocPreviewService.js` 涓?`ApiDocRequestPreview.vue` 涓交搴曟摝闄ゆ棤鐢ㄧ殑 `syncCachedParamsToTarget` 绌哄嚱鏁板畾涔変笌璋冪敤鐐癸紝淇濇寔鍏ㄩ噺浠ｇ爜闆跺啑浣欍€?
- **bug**: [2026-07-29] 淇鍒濆灏氭湭閰嶇疆鐜鍙橀噺鏃惰皟璇曢潰鏉垮彸涓婅 `鍙橀噺` / `Variables` 閾炬帴闅愯棌瀵艰嚧鏃犳硶鍏ュ彛閰嶇疆鐨勯棶棰橈細璋冩暣 `ApiEnvPopover.vue` 娓叉煋閫昏緫涓?`v-if="envSuggestions?.length || preferenceId || projectId"`锛岀‘淇濇棤璁哄垵濮嬫槸鍚︽湁宸茶В鏋愬彉閲忥紝`鍙橀噺` 閾炬帴鍧囧父椹诲彲瑙侊紝鏀寔闅忔椂鐐瑰紑缂栬緫鏂板鍙橀噺銆?
- **bug**: [2026-07-29] 淇鐐瑰嚮缂栬緫鍙橀噺鏃跺洜 `preferenceId`/`projectCode` 瀛楃涓蹭紶缁?`ApiProjectApi.getById` 瀵艰嚧鐨勫悗绔?`MethodArgumentTypeMismatchException` 绫诲瀷杞崲寮傚父锛?. 鍦?`ApiEnvParams.vue` 鐨?`toEditGroupEnvParams` 涓鍔?`isNumeric` 闃叉姢锛屽綋浼犲叆闈炵函鏁板瓧 ID锛堝 UUID 鏍煎紡鐨?`projectCode` 鎴?`shareId`锛夋椂瀹夊叏闃绘鍙戦€佸悗绔帴鍙ｈ姹傦紝鏉滅粷 Spring MVC 灏嗗瓧绗︿覆杞负 Long/Integer 澶辫触寮曞彂鐨?500 鎶ラ敊锛?. 鍦?`ApiEnvPopover.vue` 涓?`ApiRequestFormReq.vue` 涓槑纭尯鍒嗕笌閫忎紶鏁板瓧 `projectId` 涓庡亸濂戒富閿?`preferenceId`锛屼繚璇佸脊绐楄兘瀹夊叏鍙戣捣鍚庣榛樿閰嶇疆鍔犺浇锛屽悓鏃跺畬鍏ㄧ嫭绔嬫帶鍒舵湰鍦版寔涔呭寲缂撳瓨銆?
- **feat**: [2026-07-29] 浼樺寲鍙橀噺 Popover 涓庢湰鍦板彉閲忛厤缃脊绐椾氦浜掞細1. 淇濇寔 `ApiEnvPopover.vue` 涓鸿交閲忓彧璇婚瑙堟诞灞傦紝鍦?Popover 椤舵爮澧炲姞鈥滅紪杈戝彉閲?(`api.label.editVariables`)鈥濋摼鎺ワ紝鐐瑰嚮瑙﹀彂寮瑰嚭鏈湴妯″紡鐨?`ApiEnvParams.vue` 瀹屾暣閰嶇疆绐楀彛锛?. 鍦?`ApiEnvParams.vue` 寮圭獥涓ˉ鍏ㄥ浗闄呭寲鏍囩 `api.msg.saveLocalOnlyTip` 涓?`api.label.resetDefault`锛涘湪鏈湴淇濆瓨妯″紡涓旀湰鍦板瓨鍦ㄦ寔涔呭寲鏁版嵁鏃讹紝寮圭獥搴曢儴 Save/Cancel 鎸夐挳鏃佽嚜鍔ㄥ鍔?`[Reset Default / 鎭㈠榛樿]` 鎿嶄綔鎸夐挳锛岀偣鍑绘仮澶嶄负鍚庣鏁版嵁搴撻粯璁ら厤缃紱3. 娓呯悊璋冭瘯鏍忓浣欑殑鐙珛鎸夐挳锛屼繚鎸佹帴鍙ｈ鎯?Header 鐣岄潰绠€娲佸共鍑€銆?
- **bug**: [2026-07-27] 淇鍙锛堟棤鍐欐潈闄愶級椤圭洰鐨勬暟鎹ā鍨嬪強 JSON Schema 鏍戜粛鍙紪杈戜笌鎷栨嫿鐨勯棶棰橈細1. 鍦?`ApiProjectComponent.vue` 涓鍔?`readonly` 鐘舵€佹帶鍒讹紝缁欐暟鎹ā鍨嬮《閮ㄨ〃鍗曟帶浠舵坊鍔?`disabled` 鐘舵€侊紝骞跺皢 Monaco Editor 鐨?`readOnly` 閫夐」涓庨」鐩彲鍐欐潈闄愬姩鎬佽仈鍔紱2. 鍦?`ApiComponentSchemaEditTree.vue` 涓柊澧?`readonly` 灞炴€э紝鍒╃敤妯℃澘灞傞潰鐨?`:draggable="!readonly"` 浠ュ強 `<template v-if="!readonly">` 缁熶竴鎺у埗鍙鐘舵€佷笅鐨勬嫋鎷界鐢ㄤ笌缂栬緫鎸夐挳闅愯棌锛岀簿绠€鍘婚櫎浜嗗浣欑殑鍑芥暟鍐呮牎楠岋紝淇濇寔浠ｇ爜鏁存磥浼橀泤銆?
- **bug**: [2026-07-24] 淇闈炵鐞嗗憳鐢ㄦ埛鍔犺浇椤圭洰鍒嗙粍涓嬫媺妗嗘椂鍙兘瓒婃潈鑾峰彇鏃犳潈闄愬垎缁勫鑷寸殑鈥滄病鏈夋潈闄愯闂€濇姤閿欙細1. 閲嶆瀯鍚庣 `ApiGroupController.java` 涓殑 `loadProjectGroups` 鎺ュ彛锛屽己绾︽潫鍩轰簬褰撳墠鐧诲綍浜?`SecurityUtils.getLoginUserName()` 鐪熷疄韬唤杩涜 `isAdmin` 鏍￠獙涓?`checkGroupAccess` 鏉冮檺杩囨护锛岀姝㈤潪绠＄悊鍛樼敤鎴烽€氳繃 `queryVo.userName` 鍙傛暟楠楀彇鏈巿鏉冨垎缁勶紱2. 鍓嶇 `ApiProjectGroupApi.js` 鐨?`loadGroupsAndRefreshOptions` 澧炲姞 `isAdminUser()` 鍒ゆ柇淇濇姢锛岀‘淇濅粎鍦ㄧ湡姝ｇ殑绠＄悊鍛樿韩浠戒笅鎵嶅悜鍚庣浼犻€掔洰鏍?`userName` 鍙傛暟锛岄潪绠＄悊鍛樺缁堝彧鑾峰彇鏈汉鐨勬巿鏉冨垎缁勩€?
- **opt**: [2026-07-24] 鍙傜収 simple-boot-mock-server07 浼樺寲椤圭洰鍒嗙粍閫夐」涓庣敤鎴蜂俊鎭珮浜細1. 鍦?`ApiProjectGroupApi.js` 缁熶竴灏佽骞跺鍑?`renderProjectGroupLabel` 鏍煎紡鍖?helper 鍑芥暟锛屽鎵€鏈夊甫鏈夊綊灞炵敤鎴风殑椤圭洰鍒嗙粍缁熶竴鏍煎紡鍖栧睍绀轰负 `鍒嗙粍鍚?(鐢ㄦ埛鍚?`锛屽苟閫氳繃 Element Plus 鍘熺敓 `<ElText type="success" tag="b">` 缁胯壊楂樹寒灞曠ず `(鐢ㄦ埛鍚?`锛?. 鍦?`loadSelectGroups` 涓紩鍏ョǔ瀹氭帓搴忚鍒欙紝浼樺厛缃《鐧诲綍浜烘湰浜虹殑鍒嗙粍锛屽叾浣欏垎缁勬寜褰掑睘鐢ㄦ埛鍚嶅綊绫诲強 `id` 鎺掑簭锛屼娇涓嬫媺閫夐」灞傛娓呮櫚鍒嗘槑锛?. 鍦ㄥ垎浜鐞嗭紙`ApiProjectShares.vue`锛夊拰瀹氭椂浠诲姟锛坄ApiProjectTasks.vue`锛夊垪琛ㄤ腑灏嗛」鐩垎缁勬覆鏌撲负鍙偣鍑荤殑 `ElLink` 閾炬帴锛岀洿鎺ョ粦瀹氬唴鑱?`onClick={() => changeGroup(groupCode)}` 娓呯悊澶氫綑闃叉姢浠ｇ爜锛?. 淇濇寔鍏叡鍩虹缁勪欢 `control-child.vue` 闆朵慨鏀逛笌楂樺吋瀹瑰害锛屽苟閫氳繃鍓嶇 ESLint 瑙勫垯鏍￠獙銆?
- **bug**: [2026-07-24] 淇绂佺敤 AI 閰嶇疆浣嗗湪鏈埛鏂伴〉闈㈣皟鐢ㄧ敓鎴愭椂闈欓粯闄嶇骇涓洪粯璁ら厤缃苟鎴愬姛杩斿洖鏁版嵁鐨勯棶棰橈細1. 鍦?`SystemErrorConstants.java` 涓柊澧?`CODE_2012`~`CODE_2018` 缁熶竴涓氬姟閿欒鐮侊紝鍦?`messages*.properties` 琛ュ厖鍥介檯鍖栨枃妗堬紝褰诲簳娓呯悊纭紪鐮?HTTP `202` 鐘舵€佺爜锛?. 閲嶆瀯 `AiServiceImpl.java` 鐨?`resolveAiConfig` 鏍￠獙閫昏緫锛岄厤缃鐢ㄦ椂鎶涘嚭 `CODE_2013` 閿欒鐮佸苟閫氳繃 i18n 鍔ㄦ€佽繑鍥炰腑/鑻辨枃鎻愮ず锛?. 鍓嶇 `ApiCommonService.js` 褰诲簳绉婚櫎 `code` 鍒ゆ柇閫昏緫锛岄噸鏋勪负閫氱敤鐨?`resultData`锛堟垚鍔熸覆鏌?Payload锛変笌 `res.message`锛堟湭鍖呭惈 Payload 鏃剁洿鎺ユ彁绀烘秷鎭級鏋舵瀯锛屽苟閫氳繃 ESLint 瑙勫垯鏍￠獙銆?
- **bug**: [2026-07-24] 淇淇敼 AI 閰嶇疆榛樿鐘舵€佹椂鍘嗗彶璁板綍 `is_default` 琚娓呴浂鐨?Bug锛氬湪 `AiConfigController.java` 鐨?`save` 鎺ュ彛涓紝澧炲姞 `isNull("modify_from")` 闄愬畾閫昏緫锛岄槻姝㈡洿鏂伴粯璁?AI 閰嶇疆鏃惰灏嗗叏灞€鍘嗗彶璁板綍 (`modify_from IS NOT NULL`) 鐨?`is_default` 瑕嗙洊鏇存柊涓?0锛涘悓鏃跺湪 `AiConfigServiceImpl.getDefaultAiConfig` 涓鍔?`isNull("modify_from")` 绾︽潫銆?
- **opt**: [2026-07-24] 浼樺寲 AI 閰嶇疆绠＄悊鍒楄〃椤甸潰涓庡巻鍙茶褰曠増鏈脊绐楋細1. 灏嗕富鍒楄〃銆佸巻鍙茶褰曠増鏈垪琛ㄥ強缂栬緫琛ㄥ崟涓殑鈥滅姸鎬佲€濅笌鈥滆涓洪粯璁も€濆瓧娈甸『搴忕粺涓€璋冩暣涓衡€滅姸鎬佲€濆湪鍓嶃€佲€滆涓洪粯璁も€濆湪鍚庯紱2. 灏嗕富鍒楄〃涓庡巻鍙茶褰曠増鏈垪琛ㄧ殑鎵€鏈夎〃鏍煎垪瀹藉害鐢卞浐瀹?`width` 缁熶竴璋冩暣涓哄搷搴斿紡 `minWidth`锛堝 `minWidth: '120px'`, `minWidth: '150px'`, `minWidth: '100px'` 绛夛級锛岄槻姝㈠ぇ灞忔樉绀烘椂琛ㄥご鎴栨枃鏈唴瀹硅鎴柇锛?. 鍦ㄥ巻鍙茶褰曠増鏈垪琛?(`historyColumns`) 涓ˉ鍏ㄢ€滅姸鎬佲€濅笌鈥滆涓洪粯璁も€濆彧璇?Tag 鏍囩鍒楋紱4. 鈥滆涓洪粯璁も€?`isDefault`) 涓诲垪琛ㄥ垪浣跨敤鍘熺敓 `ElSwitch` 缁戝畾 `v-common-tooltip` 鍔ㄦ€佹偓娴彁绀恒€?
- **feat**: [2026-07-24] 鏀寔 AI 鐢熸垚鏃惰嚜鐢遍€夋嫨 AI 閰嶇疆涓庨粯璁ら€変腑锛?. 鍚庣 `/admin/ai/status` 涓?`/shares/ai/status` 鎺ュ彛鍗囩骇锛岃繑鍥炲寘鍚?enabled 鐘舵€併€乣defaultConfigId` 鍙婂惎鐢ㄧ殑 AI 閰嶇疆鍒楄〃 (`configs`) 鐨?`AiStatusVo`锛?. 鍚庣 `executeGenericTask` 鍜?`generateSampleBySchema` 鏀寔鎺ユ敹 `configId` 骞朵紭鍏堣皟鐢ㄦ寚瀹氶厤缃紱3. 鍓嶇鍦ㄧ敓鎴愮ず渚嬫暟鎹脊绐?(`ApiGenerateSampleWindow`) 鍜?Schema 缂栬緫鍣ㄨˉ鍏ㄦ弿杩板脊绐?(`ApiComponentSchemaEditTree`) 涓柊澧炩€淎I鎺ュ彛閰嶇疆鈥濅笅鎷夋锛屽綋鏈夊涓?AI 閰嶇疆鏃堕粯璁ら€変腑榛樿閰嶇疆锛屽苟鏀寔鐢ㄦ埛鍒囨崲閫夋嫨銆?
- **feat**: [2026-07-24] AI 鏅鸿兘琛ュ叏缂哄け鎻忚堪鍔熻兘澧炲己锛?. 鍦ㄢ€淎I 鏅鸿兘琛ュ叏缂哄け鎻忚堪鈥濆脊绐椾腑鏂板鈥滈檮鍔犳彁绀鸿瘝鈥濆琛岃緭鍏ユ锛屽厑璁哥矘璐村閮ㄦ枃妗ｃ€佸瓧娈佃鏄庢垨鑷畾涔夋彁绀鸿瘝锛涘悗绔帴鏀惰闄勫姞鎻愮ず璇嶅苟铻嶅悎鑷?prompt锛屼娇 AI 鑳界簿鍑嗗弬鑰冨閮ㄦ枃妗ｅ搴斾笌琛ュ叏 Schema 灞炴€ф弿杩帮紱2. 缁熶竴琛ㄥ崟椤?Label 瀵归綈閫昏緫锛堢簿绠€ Label 鏂囨湰涓衡€滈檮鍔犳彁绀鸿瘝鈥?鈥淎dditional Prompt鈥濓紝瀵归綈瀹藉害闄嶈嚦 110px锛夛紝骞堕噸鏋勭簿绠€鍓嶅悗绔弬鏁板鐞嗕笌寮圭獥鎵撳紑閫昏緫銆?
- **feat**: [2026-07-23] 鍗囩骇鎺ュ彛璋冭瘯鍙橀噺鎻愬彇寮曟搸锛?. 鍓嶇寮曞叆 `jsonpath-plus` 渚濊禆锛岄噸鏋?`extractVariables` 鎻愬彇閫昏緫锛屾敮鎸佹爣鍑?JSONPath 璇硶锛堝楂橀樁鏁扮粍璋撹瘝绛涢€?`$.data.list[?(@.status==1)].token` 绛夛級锛?. 鍏峰 100% 鏃ч厤缃悜涓嬪吋瀹硅兘鍔涳紝鑷姩灏嗘棤 `$` 鏍硅妭鐐瑰墠缂€鐨勬棫琛ㄨ揪寮忥紙濡?`data.token`锛夎鑼冨寲涓?`$.data.token`锛屽苟鎻愪緵 Lodash `get` 闄嶇骇鍏滃簳鏈哄埗锛?. 澧炲己閫忔槑 XML 鍝嶅簲瑙ｆ瀽鏀寔锛屽綋 Response Body 涓?XML 鏃跺埄鐢?`fast-xml-parser` 鑷姩瑙ｆ瀽涓哄唴瀛?JSON 瀵硅薄杩涜 JSONPath 鎻愬彇銆?
- **feat**: [2026-07-23] 鏂板鎸夋潯浠舵壒閲忓垹闄?娓呯┖鏁版嵁妯″瀷鍔熻兘锛?. 鍦?`ProjectComponentQueryVo` 涓柊澧?`checkOnly` 瀛楁锛屽皢鏍稿缁熻涓庡疄闄呭垹闄ゆ暣鍚堜负缁熶竴鐨?`/admin/info/detail/removeByQuery` 鎺ュ彛锛屼弗鏍兼牎楠?`DELETABLE` 鏉冮檺锛屾棦鑳藉湪棰勬鏃惰繑鍥炲尮閰嶈褰曚笌閿佸畾妯″瀷鏁帮紝鍙堣兘鍐嶆寮忓垹闄ゆ椂杩斿洖 `deletedCount` 骞舵竻鐞嗗叧鑱斿巻鍙茶褰?(`modify_from`)锛?. 鍓嶇鍩轰簬 `isDeletable` 鏉冮檺灞曠ず鈥滄竻绌烘暟鎹ā鍨嬧€濇寜閽紝涓ら樁娈佃皟鐢ㄧ粺涓€鐨?`removeByQuery` 鎺ュ彛锛屾樉寮忛厤缃?`{ loading: true }`锛屽湪棰勬涓庢寮忓垹闄よ繃绋嬩腑鍧囧睍绀哄叏灞?loading 鍔犺浇鎸囩ず鍣紝鍏堣幏鍙栨湇鍔＄缁熻鏁版嵁锛屽啀浣跨敤 `$coreConfirm` 寮圭獥鍚戠敤鎴峰睍绀烘槑缁嗭紝纭鍚庡彂璧?`checkOnly: false` 姝ｅ紡鍒犻櫎骞舵竻绌鸿鍥俱€?
- **opt**: [2026-07-23] 浼樺寲鏁版嵁妯″瀷鍒楄〃榛樿鎺掑簭閫昏緫锛氬湪 `ApiProjectInfoDetailController` 鐨?`search` 涓?`loadInfoDetails` 鎺ュ彛涓鍔?`orderByDesc("coalesce(modify_date, create_date)", "id")` 鎺掑簭锛岀‘淇濇暟鎹ā鍨嬪垪琛ㄩ粯璁ゆ寜鏈€杩戜慨鏀规椂闂达紙褰撲慨鏀规椂闂翠负绌烘椂瀹归敊鍥為€€涓哄垱寤烘椂闂?ID锛夐檷搴忔帓鍒楋紝鎻愬崌澶ч噺鏁版嵁妯″瀷鍦烘櫙涓嬬殑浣跨敤浣撻獙銆?
- **bug**: [2026-07-23] 淇鏁版嵁妯″瀷 (DTO) 鍘嗗彶璁板綍涓庣紪杈戦棶棰橈細1. 鍦?`ApiDocParseUtils.processProjectInfoDetail` 涓粺涓€姝ラ鏍￠獙 Schema 鍐呭鍚堝苟/淇濈暀鍚庣殑鐩哥瓑鎬э紝娑堥櫎 COMPONENT/SECURITY 绫诲瀷鏃犲疄璐ㄥ彉鍖栨椂浜х敓鐨勫啑浣欏巻鍙茶褰曪紱2. 閲嶆瀯 `SimpleModelUtils.mergeAuditInfo`锛岀洿鎺ュ湪鐩爣瀵硅薄涓婅缃?`modifier` 涓?`modifyDate`锛屾秷闄や慨鏀?`existsModel` 瀵艰嚧鐨勫巻鍙茶褰曚慨鏀规椂闂存埑琚薄鏌撻棶棰橈紱3. 淇宸叉湁/瀵煎叆鏁版嵁妯″瀷鏁版嵁搴撲腑 `data_version` 涓?null 瀵艰嚧涔愯閿佹洿鏂板け璐ャ€佹暟鎹湭鏇存柊涓斿弽澶嶆彃鍏ュ巻鍙茶褰曠殑闂锛堝湪瀵煎叆鍙婁繚瀛樻椂琛ュ叏榛樿鐗堟湰鍙凤紝骞跺湪鏇存柊鍓嶅 NULL `data_version` 杩涜闈欓粯淇锛夛紱4. 淇鍓嶇鍘嗗彶鍒楄〃寮圭獥鈥滀慨鏀规椂闂粹€濆垪鍘熷厛鍐欐 `property: 'createDate'` 瀵艰嚧鏈€鏂扮増鏈樉绀虹殑淇敼鏃堕棿姣斿巻鍙茬増鏈棫鐨勯棶棰橈紝绠€鍖栦负浣跨敤鏍囧噯 `formatDate(data.modifyDate || data.createDate)` 琛ㄨ揪寮忋€?
- **bug**: [2026-07-23] 淇閲嶆柊瀵煎叆/鍚屾鏂囨。鏃?x-default-auth 璁よ瘉榛樿鍊艰瑕嗙洊娓呴櫎鐨勯棶棰橈細鍦ㄥ悗绔?`ApiDocParseUtils.processProjectInfoDetail` 涓 security 绫诲瀷鐨?schemaContent 鏂板鍚堝苟閫昏緫锛岄€氳繃 `ApiSchemaContentUtils.mergeSecuritySchemaContent` 灏嗗凡淇濆瓨鐨勫悇 security schema 鐨?x-default-auth 瀛楁鍥炲～鍒版柊瀵煎叆鐨?schemaContent 涓紱鍚屾椂鍦ㄥ悎骞跺悗閲嶆柊鍋氱浉绛夋€у垽鏂紝閬垮厤浠呮坊鍔?x-default-auth 鏃犲疄璐ㄥ彉鏇存椂浜х敓涓嶅繀瑕佺殑鍘嗗彶璁板綍銆?
- **bug**: [2026-07-23] 褰诲簳淇璁よ瘉寮圭獥榛樿 Tab 閫変腑閿欒闂锛氭牴鏈師鍥犳槸涓や釜 schema 鍚屾椂鍏锋湁 hasDefaultAuth锛屾棫閫昏緫鎬婚€夊厛鍑虹幇鐨?JWT 绫诲瀷锛圓ccessToken锛夛紱淇涓轰紭鍏堥€?TOKEN 绫诲瀷锛?JWT_TOKEN锛夛紝纭繚榛樿鏄剧ず绠€鍖?Token 琛ㄥ崟銆傚悓姝ラ噸鏋勶細绉婚櫎 hasAuthValue 鍊兼鏌ワ紝鏀圭敤 x-default-auth 瀛樺湪鎬т綔涓虹粨鏋勬€у垽鏂紱hasInheritAuth 鏀逛负鍩轰簬 schema hasDefaultAuth 鑰岄潪 defaultAuthModel 鏄惁瀛樺湪銆?
- **feat**: [2026-07-22] 绠€鍖栬璇侀粯璁ゅ€笺€愭竻绌恒€戦€昏緫锛氬彧闇€鐩存帴浠庡唴瀛?Schema 涓垹闄?x-default-auth 鑺傜偣锛屽苟閲嶆柊璋冪敤 calcAuthModelBySchemas 鎭㈠琛ㄥ崟鏍囧噯榛樿鍏滃簳鍙傛暟锛屼繚鎸佺粍浠惰涓轰竴鑷翠笌浼橀泤銆?
- **bug**: [2026-07-17] 淇鍙橀噺鎻愬彇瑙勫垯鐢变簬鍋忓ソ缂撳瓨瀵艰嚧鏈纭敓鏁堢殑 Bug銆?
- **feat**: [2026-07-16] 浼樺寲鍏ㄥ眬鍙橀噺鎻愬彇鐨勮矾寰勫尮閰嶈鍒欙紝鏀寔姝ｅ垯琛ㄨ揪寮忥紝骞跺鍔犳彁鍙栨垚鍔熸彁绀?
- **feat**: [2026-07-16] 浼樺寲椤圭洰璇︽儏椤电殑鎸夐挳锛屼负璁よ瘉銆佹暟鎹ā鍨嬨€佸彉閲忛厤缃瓑鎸夐挳琛ュ厖鏁伴噺鏄剧ず
- **bug**: [2026-07-16] 淇閮ㄥ垎鍙橀噺鎻愬彇娌℃湁姝ｇ‘鏇存柊鍒板疄闄呰皟鐢ㄧ殑璇锋眰涓殑闂
- **opt**: [2026-07-16] 浼樺寲鍙橀噺閰嶇疆鏄剧ず涓庡繀濉獙璇侀€昏緫
- **feat**: [2026-07-16] 鏂板鍙橀噺閰嶇疆锛屽苟瀹炵幇鎶婂彉閲忓簲鐢ㄥ埌璇锋眰鍜岃璇佸綋涓?
- **opt**: [2026-07-16] 浼樺寲ai缂撳瓨浠ュ強閰嶇疆椤甸潰鏌ョ湅鍔熻兘
- **feat**: [2026-07-15] 瀹炵幇浜嗚姹傚悗缃剼鏈笌鍙橀噺鎻愬彇鍔熻兘锛屾敮鎸侀厤缃叏灞€鎻愬彇瑙勫垯骞堕€氳繃 JSONPath 鑷姩浠庡搷搴斾腑鎻愬彇鏁版嵁娉ㄥ叆鐜鍙橀噺锛屽畬缇庨泦鎴愮幇鏈夎璇佷綋绯讳互婊¤冻澶氭帴鍙ｉ棿鍙傛暟渚濊禆鑱斿姩锛堝 Token 鑷姩鍒锋柊鍜屾彁鍙栵級銆?
- **bug**: [2026-07-13] 淇 AI 閰嶇疆绠＄悊涓唴缃?YML 閰嶇疆涓庢暟鎹簱涓嶄竴鑷寸殑闂锛屾敮鎸佺郴缁熼厤缃潤榛樺閲忓悓姝ワ紝骞堕檺鍒跺唴缃郴缁熼厤缃殑鍒犻櫎鍙婄紪杈戙€?
- **bug**: [2026-07-13] 淇 AI 閰嶇疆绠＄悊鍒楄〃閮ㄥ垎鎯呭喌涓嬩慨鏀硅褰曞強閰嶇疆鍒楄〃鏁版嵁鏄剧ず鏍煎紡鎴栫┖椤甸潰鐨勯棶棰樸€?
- **feat**: [2026-07-13] 瀹炵幇浜嗙嫭绔嬬殑 AI 鎺ュ彛閰嶇疆绠＄悊妯″潡锛屾敮鎸佸湪鍚庡彴鍔ㄦ€佹坊鍔犮€佺紪杈戝拰鍒囨崲 AI 閰嶇疆锛屾敮鎸佸鐗堟湰鍘嗗彶绠＄悊鍙婂洖婊氾紝骞跺簾寮冧簡浠庡簲鐢ㄩ厤缃枃浠朵腑纭紪鐮佺殑璇诲彇鏂瑰紡銆?
- **opt**: [2026-07-06] 浼樺寲 AI 鏅鸿兘琛ュ叏缂哄け鎻忚堪鍔熻兘锛屽湪鍙戣捣璇锋眰鍓嶆牎楠屾槸鍚︽墍鏈夊睘鎬у潎宸插寘鍚弿杩帮紝鍑忓皯璧勬簮娴垂
- **bug**: [2026-07-06] 淇褰?AI 鏈紑鍚椂锛岀敓鎴愭ā鍨嬫弿杩版寜閽粛鏄剧ず鐨勯棶棰?
- **bug**: [2026-07-06] 淇 AI 鐢熸垚鎻忚堪鍔熻兘涓浜庡惈鏈?`items` 鐨勬暟缁勫強鍖垮悕宓屽缁撴瀯鏃犻檺璇垽鈥滃睘鎬ф弿杩扮己澶扁€濆鑷存棤闄愬脊绐楄姹傜殑闂锛涢噸鏋勫苟绮剧畝浜嗘牎楠屼唬鐮侊紝鎻愰珮鍙淮鎶ゆ€?
- **bug**: [2026-07-06] 淇寮€鍚乏鍙冲垎鏍忔ā寮忔椂椤圭洰鍒楄〃椤甸潰鍗＄墖瀹藉害琚尋鍘嬬殑闂
- **feat**: [2026-07-06] aiCache绠＄悊鍒楄〃椤甸潰澧炲姞浠诲姟绫诲瀷(cacheType)鏄剧ず鍙婄瓫閫夐」
- **bug**: [2026-07-06] 淇閲嶆柊瀵煎叆椤圭洰(闈為攣瀹氱姸鎬?浼氬鑷村湪椤甸潰涓婁慨鏀圭殑鏁版嵁妯″瀷(DTO)灞炴€escriptions涓㈠け鐨勯棶棰?
- **feat**: [2026-07-06] 鍦ㄦ暟鎹ā鍨嬬紪杈戠晫闈㈡柊澧?AI 鏅鸿兘琛ュ叏缂哄け灞炴€ф弿杩板姛鑳斤紝鏀寔涓€閿櫤鑳芥帹鏂拰鍏ㄩ噺瑕嗙洊鏇存柊锛屾瀬澶ф彁鍗囨棫鏂囨。瀹屽杽鏁堢巼
### 2026-06
- **feat**: [2026-06-30] 灏?AI 缂撳瓨绠＄悊鑿滃崟寮€鏀剧粰鎵€鏈夌敤鎴凤紝骞舵牴鎹郴缁?AI 鍔熻兘鍚敤鐘舵€佸姩鎬佹樉绀鸿鑿滃崟
- **bug**: [2026-06-30] 淇AI鐢熸垚娴嬭瘯鏁版嵁娌℃湁姝ｇ‘璁板綍鐢ㄦ埛淇℃伅鐨勯棶棰?
- **opt**: [2026-06-30] 閲嶆瀯椤圭洰鍖呯粨鏋勶紝灏?AiService 绉昏嚦 ai 鍖呬笅锛屽悎骞跺墠绔?AiApi.js 鍜?AiCacheApi.js
- **feat**: [2026-06-30] API璋冭瘯绐楀彛鐨凜URL鍔熻兘鍗囩骇锛屾柊澧炴敮鎸佸鍒朵负cURL(bash)鍜宑URL(cmd)鐨勪笅鎷夎彍鍗曢€夐」
- **bug**: [2026-06-30] 淇鍒嗕韩椤甸潰涓庡悗绔〉闈㈠湪鍚屾祻瑙堝櫒涓嬩簰鐩稿共鎵扮殑闂锛岀‘淇滱I鐢熸垚鍔熻兘浠呭湪鍚庡彴灞曠ず涓旈厤缃」鐩镐簰闅旂
- **feat**: [2026-06-30] 瀹屽杽 AI 缂撳瓨淇℃伅鏀堕泦璁捐锛屾柊澧炴彁绀鸿瘝(Prompt)銆佹搷浣滅敤鎴枫€侀」鐩甀D銆佹枃妗D銆乀oken娑堣€椾互鍙婂ぇ妯″瀷鍘熷鍝嶅簲鍜屽畬鎴愭椂闂寸瓑缁村害鐨勮褰曪紝鏂逛究鍚庣画闂鎺掓煡涓庨搴﹀璁?
- **feat**: [2026-06-29] 鏂板 AI 缂撳瓨绠＄悊椤甸潰锛屾敮鎸佺鐞嗗憳鏌ョ湅鍜岀鐞?AI 璋冪敤鐢熸垚鐨勬牱鏈暟鎹紦瀛?
- **feat**: [2026-06-29] 浼樺寲 AI 鐢熸垚鏁版嵁鍔熻兘锛屽湪鍒嗕韩椤甸殣钘?AI 鐢熸垚閫夐」锛屼繚鐣欏悗鍙板畬鏁村姛鑳斤紝渚夸簬鍐呴儴娴嬭瘯涓庢暟鎹ā鎷?
- **bug**: [2026-07-23] 褰诲簳淇"璁よ瘉"寮圭獥榛樿 Tab 閫変腑閿欒闂锛氭牴鏈師鍥犳槸 `calcSecuritySchemas` 灏嗗師濮?JWT schema 鐨?`x-default-auth` 鍚屾椂澶嶅埗缁欒櫄鎷?`$JWT_TOKEN` schema锛屽鑷翠袱鑰呴兘鏈?`hasDefaultAuth=true`锛屾棫閫昏緫鎬婚€夊厛鍑虹幇鐨?JWT 绫诲瀷锛圓ccessToken锛夛紝鏄剧ず澶嶆潅 JWT 琛ㄥ崟锛涗慨澶嶄负鏈?`hasDefaultAuth` 鏃朵紭鍏堥€?TOKEN 绫诲瀷锛坄$JWT_TOKEN`锛夎€岄潪 JWT 绫诲瀷锛岀‘淇濋粯璁ゆ樉绀虹畝鍖栫殑 Token 琛ㄥ崟鍜屾纭€笺€傚悓姝ラ噸鏋勮璇佺浉鍏冲垽鏂€昏緫锛氱Щ闄?`hasAuthValue` 鍊兼鏌ワ紝鏀圭敤 `x-default-auth` 瀛樺湪鎬э紙`hasDefaultAuth`锛変綔涓虹粨鏋勬€у垽鏂紱`hasInheritAuth` 鏀逛负鍩轰簬 schema 鐨?`hasDefaultAuth` 鑰岄潪 `defaultAuthModel` 鏄惁瀛樺湪锛涙瘡娆℃墦寮€璁よ瘉寮圭獥閮藉埛鏂?model锛涙竻绌烘搷浣滃悗鑻ユ湁 schema 榛樿鍊煎垯鑷姩閲嶆柊鍒濆鍖栥€?
- **feat**: [2026-06-29] 澧炲姞閽堝 AI 鐢熸垚璇锋眰鐨勬暟閲忛檺鍒讹紝褰撴帓闃熶腑鐨勮姹傝繃澶氭椂浼氭嫆缁濇柊鐨勮姹傦紝闃叉鑰楀敖绯荤粺绾跨▼
- **feat**: [2026-06-26] 鏀寔灏?AI 鐢熸垚鎴栨墜鍔ㄧ紪鍐欑殑鏁版嵁淇濆瓨涓烘帴鍙ｇず渚嬶紝鏂板绠＄悊鍙婂垹鏀瑰姛鑳斤紝骞朵繚鎶ゅ鍏ョ殑 OpenAPI 涓嶈鐩栧師鏈夌ず渚嬫暟鎹?
- **feat**: [2026-06-26] AI妯℃嫙鏁版嵁鐢熸垚鍔熻兘鍗囩骇涓哄紓姝ヤ换鍔℃睜澶勭悊锛屽寮哄墠绔槦鍒楃姸鎬佸搷搴旀彁绀猴紝骞舵敮鎸佽繃鏈熷強闃诲鐘舵€佺殑鏁版嵁娓呯悊
- **feat**: [2026-06-25] AI鐢熸垚鏍锋湰澧炲姞鏁版嵁搴撴寔涔呭寲缂撳瓨鍔熻兘锛屾彁楂橀噸澶嶇敓鎴愭晥鐜囧苟鑺傜害API璋冪敤鎴愭湰
- **feat**: [2026-06-24] 鏀寔澶氱鐢熸垚绀轰緥鏁版嵁鐨勫紩鎿庯細鍦ㄧ偣鍑烩€滅敓鎴愭暟鎹€濇椂寮瑰嚭瀵硅瘽妗嗭紝鍏佽鐢ㄦ埛閫夋嫨浣跨敤 `openapi-sampler`锛堝熀纭€鏁版嵁锛夈€乣Mock.js`锛堜腑鏂囬殢鏈烘暟鎹級鎴?`json-schema-faker`锛堣嫳鏂囬珮绾ock鏁版嵁锛夋潵鐢熸垚 Payload锛屾弧瓒充笉鍚屽満鏅殑璋冭瘯闇€姹?
- **opt**: [2026-06-24] 浼樺寲 API 鏂囨。灞曠ず椤甸潰锛堝彸渚ц鍥撅級鐨勯棿璺濓紝鍑忓皯鍚勬爣棰橈紙濡傛帴鍙ｆ弿杩般€佽璇併€佽姹備綋锛夊拰缁勪欢涔嬮棿鐨勯粯璁よ竟璺濆拰鍐呰竟璺濓紝浣挎帓鐗堟洿鍔犵揣鍑?
- **bug**: [2026-06-23] 淇鐢熸垚绀轰緥鏁版嵁鏃跺洜閫掑綊寮曠敤鎴栧紓甯稿祵濂楀鑷撮〉闈㈠崱椤垮苟鐢熸垚杩囧ぇPayload锛?0MB锛夌殑闂
- **bug**: [2026-06-15] 淇鏁版嵁妯″瀷缂栬緫寮圭獥涓紝鍒囨崲鍒?JSON Schema 鏍囩椤垫椂 Monaco 缂栬緫鍣ㄤ富棰橀鑹叉湭姝ｇ‘搴旂敤鐨勯棶棰?
- **opt**: [2026-06-15] DTO缂栬緫鏂板灞炴€ф敼鎴愰粯璁よ鍐呯紪杈戯紝骞朵紭鍖栫紪杈戞暟鎹ā鍨嬪脊绐椾腑boolean寮€鍏抽€夐」甯冨眬涓嶆崲琛?
- **bug**: [2026-06-15] 淇淇濆瓨鏂板缓鎺ュ彛鏃惰嫢涓嶅瓨鍦ㄩ」鐩俊鎭皟鐢?findOrCreateProjectInfo/getOrCreateMountFolder 鍥犱簨鍔″彧璇诲睘鎬ф姤閿欓棶棰?
- **opt**: [2026-06-12] 浼樺寲鐢ㄦ埛缂栬緫/淇濆瓨鏁版嵁娌℃湁鍙樺寲灏变笉鍐嶉噸澶嶄繚瀛?
- **bug**: [2026-06-12] 淇椤圭洰椤甸潰璇樉绀哄垎缁勬潈闄愮紪杈戞寜閽棶棰?
- **feat**: [2026-06-12] 椤圭洰鍒楄〃椤甸潰鏂板鏄剧ず鍒嗙粍鐢ㄦ埛鏉冮檺淇℃伅
- **feat**: [2026-06-12] 椤圭洰鍒楄〃椤甸潰鏀寔鏂板鎴栦慨鏀归」鐩垎缁勫姛鑳?
- **opt**: [2026-06-12] 椤圭洰鍒楄〃椤甸潰鏀寔鐐瑰嚮鍒嗙粍鍚嶇О蹇€熻繃婊ゅ苟鎼滅储椤圭洰锛屼笖鏈夋潈闄愭椂鏀寔鍦ㄥ崱鐗囦笂鐩存帴缂栬緫鍒嗙粍
- **opt**: [2026-06-12] 浼樺寲鐢ㄦ埛瀵嗙爜淇敼鍔熻兘
- **opt**: [2026-06-12] 浼樺寲淇濆瓨鐨勫垎缁勩€侀」鐩€佸垎浜瓑鏁版嵁娌℃湁鍙樺寲灏变笉鍐嶉噸澶嶄繚瀛?
- **bug**: [2026-06-11] 淇閮ㄥ垎鎯呭喌涓嬩慨鏀规椂闂存樉绀轰笉姝ｇ‘闂
- **opt**: [2026-06-11] 浼樺寲淇濆瓨鐨勬暟鎹病鏈夊彉鍖栧氨涓嶅啀閲嶅淇濆瓨
- **bug**: [2026-06-11] 淇璁よ瘉寮规鍋跺皵鍑虹幇閫変腑tab涓㈠け闂
- **opt**: [2026-06-11] 浼樺寲鍒嗙粍鏉冮檺閰嶇疆椤甸潰锛屾洿鏂逛究閰嶇疆
- **opt**: [2026-06-11] 浼樺寲瀵煎嚭鐢熸垚浠ｇ爜瀹夊叏鎬?
- **version**: [2026-06-11] 鏇存柊鐗堟湰鍙?
- **bug**: [2026-06-11] 淇鍦ㄥ垵濮嬪鍏ラ」鐩殑鏃跺€欒鍔犺浇鏂囦欢澶归棶棰?
- **feat**: [2026-06-11] 鐢ㄦ埛鍏宠仈鏃ュ織澧炲姞鏀寔鏈夋潈闄愮殑椤圭洰鏌ヨ杩囨护
- **opt**: [2026-06-11] 浼樺寲涓€浜涙搷浣滄潈闄愭帶鍒堕槻姝㈣秺鏉?
- **opt**: [2026-06-11] 浼樺寲涓€浜沝oc鍔犺浇閿欒鐨勪俊鎭睍绀?
- **opt**: [2026-06-11] 澧炲姞璋冭瘯浼犻€抲rl楠岃瘉锛屾彁鍗囧畨鍏ㄦ€?
- **opt**: [2026-06-11] 浼樺寲share鏂囨。鐨勫垽鏂潈闄愶紝闃叉鍔犺浇鏃犳潈闄愭暟鎹?
- **feat**: [2026-06-11] 鏃ュ織绠＄悊鍔熻兘寮€鏀剧粰鏅€氱敤鎴凤紝鍙互鏌ョ湅鑷繁鐨勬棩蹇?
- **opt**: [2026-06-10] 浼樺寲鐢ㄦ埛绠＄悊鐩稿叧鏉冮檺鎺у埗
- **feat**: [2026-06-09] 澧炲姞鏃ュ織娓呯悊浠诲姟锛屾竻鐞嗗巻鍙叉棩蹇?
- **opt**: [2026-06-08] 浼樺寲瀵煎叆鏂囨。鎬ц兘
- **docs**: [2026-06-08] 鏂板寮€婧愭枃妗ｇ綉绔欓〉闈?
- **feat**: [2026-06-08] 閲嶆柊璁捐鏂扮殑api鏂囨。椤圭洰鐨刲ogo

### 2026-02
- **opt**: [2026-02-08] 浼樺寲鍔犺浇鏂囨。璇︽儏鐨刲oading
- **opt**: [2026-02-08] 鍒嗕韩鏂囨。瀵嗙爜楠岃瘉鎴愬姛鍚庝粠椤甸潰娓呴櫎
- **feat**: [2026-02-08] 鐢ㄦ埛鏂板缂栬緫鏂板闅忔満鐢熸垚瀵嗙爜鍔熻兘
- **feat**: [2026-02-08] 椤圭洰杩愯鐜缂栬緫澧炲姞鎷栧姩鎺掑簭鍔熻兘
- **bug**: [2026-02-08] 淇鍒嗕韩椤靛垏鎹富棰樻椂闂儊闂
- **bug**: [2026-02-08] 淇褰撳墠椤甸潰鍋氭帴鍙ｉ瑙堟椂涓嶈兘璋冩暣瀹藉害闂
- **bug**: [2026-02-02] 淇鏀惰捣鎴栧睍寮€宸︿晶鑿滃崟鏃跺姩鐢讳涪澶遍棶棰?
- **bug**: [2026-02-02] 淇宸︿晶鑿滃崟鏀惰捣鏉ユ椂鎷栧姩鏉′緷鐒跺瓨鍦ㄧ殑闂
- **opt**: [2026-02-02] 浼樺寲椤圭洰缂栬緫椤甸潰寮圭獥灞傛牱寮?
- **bug**: [2026-02-02] 淇split鐨別lementSizes璁＄畻涓㈠け闂
- **feat**: [2026-02-02] 宸︿晶鑿滃崟妯″紡澧炲姞split鎷栧姩澶у皬璋冩暣鍔熻兘
- **bug**: [2026-02-02] 淇澧炲姞鐧诲嚭鑿滃崟寮曡捣鐨勫鑸涪澶遍棶棰?
- **bug**: [2026-02-01] 淇涓婚鍒囨崲鏃堕儴鍒嗘儏鍐靛姩鐢诲紓甯?

### 2026-01
- **bug**: [2026-01-31] 淇閮ㄥ垎鎯呭喌涓媡ab鏄剧ず寮傚父闂
- **opt**: [2026-01-30] 浼樺寲鍒嗕韩鏂囨。鐨勫瘑鐮佸～鍐欓〉闈㈡牱寮?
- **bug**: [2026-01-30] 淇鏂囨。鍔犺浇澶辫触鏄烦杞櫥褰曠殑鎻愮ず寮傚父闂
- **feat**: [2026-01-30] 鍒嗕韩椤甸潰濡傛灉鏈夊瘑鐮侊紝鏂板涓€涓櫥鍑鸿彍鍗?
- **bug**: [2026-01-30] 淇鍔犺浇md鏂囨。鏃犳潈闄愭椂娌℃湁璺宠浆鐧诲綍椤甸棶棰?
- **opt**: [2026-01-30] 浼樺寲鍒嗕韩椤甸潰涔熸敮鎸佷富棰樺垏鎹㈠姩鐢?
- **opt**: [2026-01-30] 浼樺寲澶氭爣绛炬ā寮忎互鍙婇潰鍖呭睉鏍峰紡
- **feat**: [2026-01-30] 鐧诲綍椤甸潰閲嶆柊鐢ˋI璁捐浼樺寲
- **feat**: [2026-01-30] 瀵规暣涓〉闈㈢殑鏁翠綋椋庢牸鏍峰紡璋冩暣
- **feat**: [2026-01-29] add GitHub Actions workflow for building, releasing, and publishing Docker images.
- **bug**: [2026-01-28] 淇API鏂囨。缂栬緫椤甸潰鍜屽睍绀洪〉闈㈡牱寮忎笉涓€鑷撮棶棰?
- **feat**: [2026-01-28] API椤圭洰绠＄悊鏂板鍦ㄥ皬灞忓箷涓嬪乏渚у脊鍑鸿彍鍗?
- **bug**: [2026-01-28] 淇鍒嗕韩椤靛乏渚ф偓娴寜閽尅浣忛儴鍒嗗彸杈归儴鍒嗛棶鏈棶棰?
- **opt**: [2026-01-28] 浼樺寲鏂囨。鏍戣妭鐐圭Щ鍔ㄧ粨鏉熸椂寮规纭鍙栨秷鏃跺埛鏂版満鍒?
- **feat**: [2026-01-28] 宸︿晶API鏍戣彍鍗曠Щ鍔ㄦ椂澧炲姞纭鎿嶄綔锛岄槻姝㈣鎿嶄綔
- **opt**: [2026-01-28] 浼樺寲鍥炲埌椤堕儴浠ュ強鍏ㄥ睆鎸夐挳鏄剧ず浣嶇疆涓庢牱寮?
- **feat**: [2026-01-27] 鍚庡彴椤甸潰鍙充笅瑙掑鍔犱竴涓叏灞忔寜閽柟渚垮睍绀烘洿澶氬厓绱?
- **bug**: [2026-01-27] 淇瀵煎嚭鏂囨。鏍戝ぇ灏忓啓寮曡捣鐨勭瓫閫変笉姝ｇ‘闂
- **bug**: [2026-01-17] 淇鍒嗕韩椤甸潰閮ㄥ垎monaco缂栬緫鍣ㄥ嚭鐜颁富棰樹笉涓€鑷寸殑鎯呭喌
- **opt**: [2026-01-17] 浼樺寲璇锋眰鍙傛暟绛夎緭鍏ユtabindex鎺у埗閫昏緫
- **opt**: [2026-01-17] 浼樺寲鏂囨。鍒嗕韩璁よ瘉澶辫触鏃剁殑璺宠浆閫昏緫
- **bug**: [2026-01-17] 淇璁よ瘉閰嶇疆涓嶈兘娓呯┖浠ュ強鍐嶆鎵撳紑鍙兘鍑虹幇涓嶄竴鑷寸殑鎯呭喌
- **bug**: [2026-01-16] 淇浠ｇ悊璁块棶鏃惰浣忕櫥褰曠姸鎬佸紩璧风殑璁块棶閿欒闂
- **bug**: [2026-01-16] 淇monaco缂栬緫鍣ㄥ脊鍑哄眰鍦ㄥ垎浜〉闈㈠垏鎹富棰樻棤鏁堥棶棰?
- **opt**: [2026-01-16] monaco缂栬緫鍣ㄥ拰涓婚鑱斿姩璋冩暣
- **feat**: [2026-01-16] 瀵规瘮宸ュ叿澧炲姞璇█閫夐」浠ュ強鏍煎紡鍖栧姛鑳?
- ... (log continues)
- **feat**: [2026-01-15] 鑿滃崟涓姣斿伐鍏锋柊澧炶浣忎笂娆¤緭鍏ュ姛鑳?
- **feat**: [2026-01-15] 浠ｇ爜宸ュ叿鏂板鎸夌収琛ㄦ牸鏌ョ湅蹇嵎鍥炬爣锛屾樉绀烘晥鏋滅粺涓€
- **bug**: [2026-01-14] 淇涓€浜涗緷璧栧彉鏇村紩璧风殑閿欒

---
*注：本日志基于完整的 Git 提交历史进行深度挖掘与分类汇总。*
- **opt**: [2026-08-06] 优化项目分组与权限安全控制：重构 `DashboardController` 等控制器的后端查询 SQL 拼接逻辑，利用 `apiProjectAccessService` 方法处理分组权限，消除重复查询构造代码。
- **opt**: [2026-08-07] 优化 dashboard 统计大盘代码结构与样式冗余：1. 在 `DashboardTrendChart.vue` 中修复暗黑模式下网格线过亮的问题，将坐标系背景网格线 `splitLine` 颜色重置为 Element Plus 动态 CSS 变量 `var(--el-border-color-lighter)`；2. 移除 `DashboardRecentShares.vue`、`DashboardRecentImports.vue` 等小组件中重复的 `.dashboard-card` 样式定义，改用内置属性 `body-style="flex: 1; padding: 0; overflow: hidden;"` 消除代码重复；3. 将活动列表行项重复样式 `.activity-item` 统一抽取沉淀至全局样式 `src/assets/main.css` 中；4. 所有修改均已通过严格的 ESLint 规范检验。
