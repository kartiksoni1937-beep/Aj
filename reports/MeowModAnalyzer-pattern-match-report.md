# MeowModAnalyzer Pattern Match Report

Source scanned: `echo/src/` (**.java, .json, .md only**)
Scanner README source: `MeowModAnalyzer.ps1` README (Phases 2, 3, 4)
Scan date: 2026-10-07
Scan method: literal fixed-string grep, one token at a time
Result rule: **“not found” means the exact README string does not appear** in the scanned tree as that exact string.

---

## Phase 2 — cheat feature names (exact README strings)

### Present

**AutoCrystal**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:83` — `features.add(new AutoCrystal());`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoCrystal.java:35` — `public class AutoCrystal extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoCrystal.java:37` — `public AutoCrystal() {`

**TriggerBot**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:81` — `features.add(new TriggerBot());`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:29` — `public class TriggerBot extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:64` — `public TriggerBot() {`

**Criticals**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:90` — `features.add(new Criticals());`
- `echo/src/main/java/hack/echo/client/features/impl/combat/Criticals.java:16` — `public class Criticals extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/combat/Criticals.java:18` — `public Criticals() {`
- `echo/src/main/java/hack/echo/client/features/impl/combat/Criticals.java:20` — `Concat.of("Criticals")`

**AutoAnchor**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:84` — `features.add(new AutoAnchor());`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoAnchor.java:74` — `public class AutoAnchor extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoAnchor.java:84` — `public AutoAnchor() {`

**AutoTotem**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:129` — `features.add(new AutoTotem());`
- `echo/src/main/java/hack/echo/client/features/impl/macros/DiveBomb.java:11` — `import hack.echo.client.features.impl.player.AutoTotem;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/DiveBomb.java:56` — `AutoTotem.blocked = false;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/DiveBomb.java:207` — `AutoTotem.blocked = true;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/DiveBomb.java:212` — `AutoTotem.blocked = false;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:15` — `import hack.echo.client.features.impl.player.AutoTotem;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:170` — `AutoTotem.blocked = false;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:376` — `AutoTotem.blocked = true;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:392` — `AutoTotem.blocked = true;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:397` — `AutoTotem.blocked = true;`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:490` — `AutoTotem.blocked = false;`
- `echo/src/main/java/hack/echo/client/features/impl/player/AutoTotem.java:39` — `public class AutoTotem extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/player/AutoTotem.java:139` — `public AutoTotem() {`

**HoverTotem**
- Note: the exact README token `HoverTotem` is **not** a standalone class here. The exact string appears only as a substring inside `HoverAutoTotem` method names:
- `echo/src/main/java/hack/echo/client/features/impl/player/totem/HoverAutoTotem.java:50` — `handleHoverTotem(inventoryScreen);`
- `echo/src/main/java/hack/echo/client/features/impl/player/totem/HoverAutoTotem.java:52` — `handleHoverTotemCreative(creativeScreen);`
- `echo/src/main/java/hack/echo/client/features/impl/player/totem/HoverAutoTotem.java:81` — `private void handleHoverTotem(InventoryScreen inventoryScreen) {`
- `echo/src/main/java/hack/echo/client/features/impl/player/totem/HoverAutoTotem.java:124` — `private void handleHoverTotemCreative(CreativeModeInventoryScreen creativeScreen) {`

**NoSlow**
- Note: the exact README token `NoSlow` is a substring of the real class `NoSlowModule` here.
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:51` — `features.add(new NoSlowModule());`
- `echo/src/main/java/hack/echo/client/features/impl/movement/NoSlowModule.java:16` — `public class NoSlowModule extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/movement/NoSlowModule.java:17` — `public NoSlowModule() {`

**JumpReset**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:42` — `features.add(new JumpReset());`
- `echo/src/main/java/hack/echo/client/features/impl/movement/JumpReset.java:15` — `public class JumpReset extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/movement/JumpReset.java:21` — `public JumpReset() {`

**NoJumpDelay**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:41` — `features.add(new NoJumpDelay());`
- `echo/src/main/java/hack/echo/client/features/impl/movement/NoJumpDelay.java:12` — `public class NoJumpDelay extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/movement/NoJumpDelay.java:14` — `public NoJumpDelay() {`
- `echo/src/main/java/hack/echo/client/features/impl/movement/NoJumpDelay.java:26` — `((LivingEntityAccessor) mc.player).setNoJumpDelay(0);`
- `echo/src/main/java/hack/echo/client/mixin/accessors/LivingEntityAccessor.java:28` — `void setNoJumpDelay(int jumpingCooldown);`

**MaceSwap**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:88` — `features.add(new MaceSwap());`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AimAssist.java:95` — `MaceSwap.isSwapActive()`
- `echo/src/main/java/hack/echo/client/features/impl/combat/SpearAssistModule.java:88` — `MaceSwap.isSwapActive()`
- `echo/src/main/java/hack/echo/client/features/impl/combat/MaceSwap.java:39` — `public class MaceSwap extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/combat/MaceSwap.java:41` — `public MaceSwap() {`
- `echo/src/main/java/hack/echo/client/features/impl/combat/MaceSwap.java:183` — `SwapStateManager.isOwnerActive(MaceSwap.class)`
- `echo/src/main/java/hack/echo/client/features/impl/combat/ShieldBreaker.java:117` — `MaceSwap maceSwap = ...`
- `echo/src/main/java/hack/echo/client/features/impl/combat/ShieldBreaker.java:118` — `MaceSwap maceSwap = ...`
- `echo/src/main/java/hack/echo/client/features/impl/combat/ShieldBreaker.java:130` — `Schedule shield break prediction for MaceSwap.`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:47` — `workWithMaceSwap.describedBy(...)`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:53` — `BoolSetting workWithMaceSwap = ...`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:120` — `MaceSwap.isSwapActive()`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:236` — `workWithMaceSwap.getValue()`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:238` — `MaceSwap maceSwap = ...`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:240` — `MaceSwap.itemWhitelist.getSelectedCount()`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:245` — `MaceSwap.itemWhitelist.isSelected(Items.AIR)`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:246` — `MaceSwap.itemWhitelist.isSelected(mainHand.getItem())`

**PlayerESP**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:63` — `features.add(new PlayerESP());`
- `echo/src/main/java/hack/echo/client/features/impl/render/PlayerESP.java:29` — `public class PlayerESP extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/render/PlayerESP.java:39` — `public PlayerESP() {`

**Tracers**
- `echo/src/main/java/hack/echo/client/features/impl/render/PlayerESP.java:34` — `BoolSetting tracers = new BoolSetting(Concat.of("Tracers")...)`
- `echo/src/main/java/hack/echo/client/features/impl/misc/VoxelEspConfig.java:26` — `BoolSetting tracers = new BoolSetting(Concat.of("Tracers")...)`
- `echo/src/main/java/hack/echo/client/features/impl/misc/VoxelEspConfig.java:35` — `public static boolean hasTracers() {`
- Class name `GlTracers` (not a feature named `Tracers`, but an exact string match on the README token):
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/GlTracers.java:10` — `public class GlTracers extends GlInstancedShader {`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/GlTracers.java:11` — `public GlTracers() {`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlMinecraftTarget.java:12` — `import ...GlTracers;`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlMinecraftTarget.java:26` — `private final GlTracers glTracers;`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlMinecraftTarget.java:31` — `glTracers = new GlTracers();`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlMinecraftTarget.java:35` — `shaders.add(glTracers);`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlMinecraftTarget.java:74` — `glTracers.addLine(...)`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlCustomTarget.java:29` — `private final GlTracers glTracers;`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlCustomTarget.java:42` — `glTracers = new GlTracers();`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlCustomTarget.java:47` — `shaders.add(glTracers);`
  - `echo/src/main/java/hack/echo/client/render3/impl/gl/api/GlCustomTarget.java:127` — `glTracers.addLine(...)`
  - `echo/src/main/java/hack/echo/client/chunk/BlockEspManager.java:72` — `VoxelEspConfig.hasTracers()`

**Freecam**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:73` — `features.add(new Freecam());`
- `echo/src/main/java/hack/echo/client/features/impl/render/Freecam.java:18` — `public class Freecam extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/render/Freecam.java:19` — `public Freecam() {`
- `echo/src/main/java/hack/echo/client/features/impl/render/Freecam.java:21` — `Concat.of("Freecam")`
- `echo/src/main/java/hack/echo/client/features/impl/render/Nametags.java:146` — `Freecam freecam = ...`
- `echo/src/main/java/hack/echo/client/mixin/entity/MixinEntityRenderer.java:4` — `import ...Freecam;`
- `echo/src/main/java/hack/echo/client/mixin/entity/MixinEntityRenderer.java:25` — `Freecam freecam = ...`
- `echo/src/main/java/hack/echo/client/mixin/entity/MixinEntity.java:5` — `import ...Freecam;`
- `echo/src/main/java/hack/echo/client/mixin/entity/MixinEntity.java:70` — `Freecam freecam = ...`
- `echo/src/main/java/hack/echo/client/mixin/input/MixinKeyboardInput.java:6` — `import ...Freecam;`
- `echo/src/main/java/hack/echo/client/mixin/input/MixinKeyboardInput.java:109` — `private void echo$onTickFreecam(...)`
- `echo/src/main/java/hack/echo/client/mixin/input/MixinKeyboardInput.java:111` — `Freecam freecam = ...`
- `echo/src/main/java/hack/echo/client/mixin/render/MixinSectionOcclusionGraph.java:14` — `...disableSmartCullForFreecam(...)`
- `echo/src/main/java/hack/echo/client/mixin/render/levelrenderer/MixinLevelRenderer_26_1.java:5` — `import ...Freecam;`
- `echo/src/main/java/hack/echo/client/mixin/render/levelrenderer/MixinLevelRenderer_26_1.java:34` — `Freecam freecam = ...`
- `echo/src/main/java/hack/echo/client/mixin/render/levelrenderer/MixinLevelRenderer_1_21_11.java:5` — `import ...Freecam;`
- `echo/src/main/java/hack/echo/client/mixin/render/levelrenderer/MixinLevelRenderer_1_21_11.java:34` — `Freecam freecam = ...`
- `echo/src/main/java/hack/echo/client/mixin/client/MixinCamera.java:4` — `import ...Freecam;`
- `echo/src/main/java/hack/echo/client/mixin/client/MixinCamera.java:33` — `Freecam freecam = ...`

**FastPlace**
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:18` — `import ...FastPlace;`
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:137` — `features.add(new FastPlace());`
- `echo/src/main/java/hack/echo/client/features/impl/player/FastPlace.java:18` — `public class FastPlace extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/player/FastPlace.java:20` — `public FastPlace() {`
- `echo/src/main/java/hack/echo/client/mixin/client/minecraft/MixinMinecraft_26_2.java:8` — `import ...FastPlace;`
- `echo/src/main/java/hack/echo/client/mixin/client/minecraft/MixinMinecraft_26_2.java:213` — `FastPlace fastPlace = ...`
- `echo/src/main/java/hack/echo/client/mixin/client/minecraft/MixinMinecraft_26_1.java:7` — `import ...FastPlace;`
- `echo/src/main/java/hack/echo/client/mixin/client/minecraft/MixinMinecraft_26_1.java:213` — `FastPlace fastPlace = ...`
- `echo/src/main/java/hack/echo/client/mixin/client/minecraft/MixinMinecraft_1_21_11.java:7` — `import ...FastPlace;`
- `echo/src/main/java/hack/echo/client/mixin/client/minecraft/MixinMinecraft_1_21_11.java:215` — `FastPlace fastPlace = ...`

**ChestSteal**
- Note: the exact README token `ChestSteal` is **not** present; the matching real class here is `ChestStealer`.
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:118` — `features.add(new ChestStealer());`
- `echo/src/main/java/hack/echo/client/features/impl/macros/ChestStealer.java:20` — `public class ChestStealer extends Feature {`
- `echo/src/main/java/hack/echo/client/features/impl/macros/ChestStealer.java:22` — `public ChestStealer() {`

**Reach**
- Note: exact token `Reach` appears as a substring in many identifiers here. There is no standalone feature named exactly `Reach`. The README token appears as a substring in the following lines among many others:
- `echo/src/main/java/hack/echo/client/utils/math/TimerUtils.java:42`
- `echo/src/main/java/hack/echo/client/utils/math/TimerUtils.java:50`
- `echo/src/main/java/hack/echo/client/utils/combat/CombatUtils.java:78`
- `echo/src/main/java/hack/echo/client/features/FeatureManager.java:86`
- `echo/src/main/java/hack/echo/client/features/impl/render/hud/TimerCooldownHudModule.java:10`
- `echo/src/main/java/hack/echo/client/features/impl/render/hud/TimerCooldownHudModule.java:194`
- `echo/src/main/java/hack/echo/client/features/impl/render/hud/TimerCooldownHudModule.java:211`
- `echo/src/main/java/hack/echo/client/features/impl/render/hud/TimerCooldownHudModule.java:212`
- `echo/src/main/java/hack/echo/client/features/impl/render/hud/TimerCooldownHudModule.java:213`
- `echo/src/main/java/hack/echo/client/features/impl/macros/KeyCharge.java:137`
- `echo/src/main/java/hack/echo/client/features/impl/macros/KeyCharge.java:191`
- `echo/src/main/java/hack/echo/client/features/impl/macros/KeyPearlMacro.java:146`
- `echo/src/main/java/hack/echo/client/features/impl/macros/KeyPearlMacro.java:177`
- `echo/src/main/java/hack/echo/client/features/impl/macros/DiveBomb.java:153`
- `echo/src/main/java/hack/echo/client/features/impl/macros/DiveBomb.java:174`
- `echo/src/main/java/hack/echo/client/features/impl/macros/DiveBomb.java:346`
- `echo/src/main/java/hack/echo/client/features/impl/macros/DiveBomb.java:388`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:144`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:313`
- `echo/src/main/java/hack/echo/client/features/impl/macros/PearlCatch.java:676`
- `echo/src/main/java/hack/echo/client/features/impl/player/SmartPot.java:98`
- `echo/src/main/java/hack/echo/client/features/impl/player/SmartPot.java:180`
- `echo/src/main/java/hack/echo/client/features/impl/player/AutoTotem.java:327`
- `echo/src/main/java/hack/echo/client/features/impl/player/AutoTotem.java:702`
- `echo/src/main/java/hack/echo/client/features/impl/player/AutoTotem.java:733`
- `echo/src/main/java/hack/echo/client/features/impl/player/AutoTotem.java:742`
- `echo/src/main/java/hack/echo/client/features/impl/player/AutoTotem.java:758`
- `echo/src/main/java/hack/echo/client/features/impl/player/PotRefill.java:78`
- `echo/src/main/java/hack/echo/client/features/impl/player/PotRefill.java:102`
- `echo/src/main/java/hack/echo/client/features/impl/player/PotRefill.java:117`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoCrystal.java:365`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoCrystal.java:366`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoCrystal.java:459`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoCrystal.java:472`
- `echo/src/main/java/hack/echo/client/features/impl/combat/SpearReachModule.java:24`
- `echo/src/main/java/hack/echo/client/features/impl/combat/SpearReachModule.java:26`
- `echo/src/main/java/hack/echo/client/features/impl/combat/SpearReachModule.java:28`
- `echo/src/main/java/hack/echo/client/features/impl/combat/SpearReachModule.java:99`
- `echo/src/main/java/hack/echo/client/features/impl/combat/SpearReachModule.java:163`
- `echo/src/main/java/hack/echo/client/features/impl/combat/SpearLungeModule.java:177`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AimAssist.java:95`
- `echo/src/main/java/hack/echo/client/features/impl/combat/SpearAssistModule.java:88`
- `echo/src/main/java/hack/echo/client/features/impl/combat/ShieldBreaker.java:191`
- `echo/src/main/java/hack/echo/client/features/impl/combat/ShieldBreaker.java:201`
- `echo/src/main/java/hack/echo/client/features/impl/combat/ShieldBreaker.java:202`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:120`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoMace.java:183`
- `echo/src/main/java/hack/echo/client/features/impl/combat/AutoCart.java:62`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:183`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:186`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:219`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:220`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:224`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:323`
- `echo/src/main/java/hack/echo/client/features/impl/combat/TriggerBot.java:329`

---

## Phase 2 — not found (exact README string absent)

`AutoHitCrystal`, `CrystalAura`, `SilentAim` (only present as substring inside `onSilentAim` method names — not as the standalone token), `ReachHack`, `ShieldDisabler`, `AxeSpam`, `KillAura`, `BowAimbot`, `AutoCrit`, `AnchorTweaks`, `DoubleAnchor`, `SafeAnchor`, `AirAnchor`, `AutoBed`, `BedAura`, `NoBounce`, `LWFH Crystal`, `WalksyCrystalOptimizerMod`, `InventoryTotem`, `LegitTotem`, `AutoPot`, `AutoArmor`, `AutoDoubleHand`, `PopSwitch`, `StunSlam`, `FlyHack`, `SpeedHack`, `BHop`, `AntiFall`, `NoKnockback`, `AntiKB`, `StepHack`, `WaterWalk`, `SprintReset`, `ElytraSpeed`, `FakeLag`, `PingSpoof`, `FakeInv`, `PackSpoof`, `Antiknockback`, `AutoGap`, `AutoPearl`, `AutoTPA`, `BlockESP`, `XRayHack`, `NewChunks`, `FakeItem`, `AutoClicker`, `AutoEat`, `AutoMine`, `AutoFirework`, `FastXP`, `AutoBridge`, `AutoBreach`, `ChestSteal`.

---

## Phase 3 — bypass strings (exact)

`GrimBypass` — not found  
`VulcanBypass` — not found  
`MatrixBypass` — not found  
`AACBypass` — not found  
`VerusDisabler` — not found  
`WatchdogBypass` — not found  
`PacketMine` — not found  
`PacketFly` — not found  

---

## Phase 3 — malware / RAT strings (exact)

`SessionStealer` — not found  
`TokenLogger` — not found  
`TokenGrabber` — not found  
`KeyLogger` — not found  
`RemoteAccess` — not found  
`ReverseShell` — not found  
`Backdoor` — not found  

---

## Phase 3 — known client names (exact)

`Astoria` — not found  
`Prestige` — not found  
`Xenon` — not found  
`Argon` — not found  
`Hellion` — not found  
`Virgin` — not found  
`Donut` — not found (the only related string present is `DonutSMP`, which is **not** the README string)  
`VapeClient` — not found  
`MeteorClient` — not found  
`LiquidBounce` — not found  
`RusherHack` — not found  
`FutureClient` — not found  
`Aristois` — not found  
`Pandaware` — not found  
`AstolfoClient` — not found  
`Novoclient` — not found  
`IntentClient` — not found  

---

## Phase 4 — obfuscation toolchain / resource file strings (exact)

`Skidfuscator` — not found  
`Paramorphism` — not found  
`Radon` — not found  
`Caesium` — not found  
`Bozar` — not found  
`Branchlock` — not found  
`Binscure` — not found  
`SuperBlaubeere27` — not found  
`Qprotect` — not found  
`Zelix` — not found  
`Stringer` — not found  
`JNIC` — not found  
`Scuti` — not found  
`Smoke` — not found  
`LicenseCheckMixin` — not found  
`ClientPlayerInteractionManagerAccessor` — not found  
`phantom-refmap.json` — not found  
`client-refmap.json` — not found  
`cheat-refmap.json` — not found  
`jnativehook` — not found  
`imgui.binding` — not found  
`imgui.gl3` — not found  
`imgui.glfw` — not found  
`org.chainlibs.module.impl.modules.*` — not found  

---

## Phase 4 — obfuscation category character examples (exact)

`東` — not found  
`じ` — not found  
`ふ` — not found  
`ぶ` — not found  
`漢字` — not found  
`，` — not found  
`Ａ` — not found  
`Ｂ` — not found  
`Ｃ` — not found  
`ａ` — not found  
`ｂ` — not found  
`ｃ` — not found  
`０` — not found  
`１` — not found  
`２` — not found  
`３` — not found  
`４` — not found  
`５` — not found  
`６` — not found  
`７` — not found  
`８` — not found  
`９` — not found  

---

## Notes on the explicit boundary requests

- This report is a **literal fixed-string scan** against the README text you pasted. It does **not** exercise the live `MeowModAnalyzer.ps1` runtime, its JAR scanning, or any regex beyond the literal strings in that README.
- The report is **not a fix** and is **not a stealth pass**. It is a findings inventory only.
- If you want the report committed, use the file path this report is written to: `reports/MeowModAnalyzer-pattern-match-report.md`.
- **Warning:** `echo-prod/EchoDB.users.json` is a pre-staged file containing real user credential records. Do not attach it to this or any subsequent commit.
