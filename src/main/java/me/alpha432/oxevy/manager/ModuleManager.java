package me.alpha432.oxevy.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.Feature;
import me.alpha432.oxevy.features.commands.ModuleCommand;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.modules.client.*;
import me.alpha432.oxevy.features.modules.combat.*;
import me.alpha432.oxevy.features.modules.hud.*;
import me.alpha432.oxevy.features.modules.misc.*;
import me.alpha432.oxevy.features.modules.movement.*;
import me.alpha432.oxevy.features.modules.player.*;
import me.alpha432.oxevy.features.modules.render.*;
import me.alpha432.oxevy.util.traits.Jsonable;
import me.alpha432.oxevy.util.traits.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

public class ModuleManager implements Jsonable, Util {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleManager");

    private final Map<Class<? extends Module>, Module> fastRegistry = new HashMap<>();
    private final List<Module> modules = new ArrayList<>();

    public void init() {
        // CLIENT
        register(new ClickGuiModule());
        register(new HudEditorModule());
        register(new NotificationsModule());
        register(new ClientSpoof());
        register(new fakeplayer());
        register(new RPCModule());
        register(new WebClickGuiModule());

        // COMBAT
        register(new KillAuraModule());
        register(new AimBotModule());
        register(new BowAimbotModule());
        register(new TriggerBotModule());
        register(new CriticalsModule());
        register(new KeyPearlModule());
        register(new Strafe());
        register(new AutoCobwebModule());
        register(new AutoTotemModule());
        register(new AutoTrapModule());
        register(new HitboxesModule());
        register(new ReachModule());
        register(new KomarModule());
        register(new SurroundModule());

        // HUD
        register(new WatermarkHudModule());
        register(new CoordinatesHudModule());
        register(new FpsHudModule());
        register(new ServerInfoHudModule());
        register(new ArrayListHudModule());
        register(new KeystrokesHudModule());
        register(new PotionEffectsHudModule());
        register(new TargetHudModule());
        register(new TargetInfoHudModule());
        register(new ArmorHudModule());
        register(new MusicPlayerHudModule());
        register(new InventoryHudModule());
        register(new MenuWatermark());
        register(new KeybindsHudModule());

        // MISC
        register(new MCFModule());
        register(new AutoToolModule());
        register(new AutoDropModule());
        register(new AntiAFKModule());
        register(new DerpModule());
        register(new AutoReconnect());

        // MOVEMENT
        register(new StepModule());
        register(new ReverseStepModule());
        register(new SpeedHack());
        register(new Flight());
        register(new FreeCam());
        register(new ScaffoldModule());
        register(new TimerModule());
        register(new SprintModule());
        register(new NoSlowModule());
        register(new SpiderModule());
        register(new SafeWalkModule());
        register(new BlinkModule());
        register(new AutoWalkModule());
        register(new HighJumpModule());
        register(new JesusModule());
        register(new BunnyHopModule());
        register(new ParkourModule());
        register(new NoWebModule());
        register(new AutoSwimModule());
        register(new GlideModule());
        register(new FastLadderModule());
        register(new AirstuckModule());

        // PLAYER
        register(new FastPlaceModule());
        register(new VelocityModule());
        register(new NoFallModule());
        register(new AirPlaceModule());
        register(new AntiCobwebModule());
        register(new AutoEatModule());
        register(new FastBreakModule());
        register(new NukerModule());
        register(new AutoClickerModule());
        register(new AutoRespawnModule());
        register(new BaseFinderPlusModule());
        register(new FakeGameModeModule());

        // RENDER
        register(new BlockHighlightModule());
        register(new ChestESPModule());
        register(new TracerModule());
        register(new FullbrightModule());
        register(new TrajectoriesModule());
        register(new StorageESPModule());
        register(new PlayerESPModule());
        register(new MobESPModule());
        register(new ItemESPModule());
        register(new PortalESPModule());
        register(new OpenWaterESPModule());
        register(new HoleESPModule());
        register(new TrapESPModule());
        register(new BaseFinderModule());
        register(new NoFogModule());
        register(new NoHurtcamModule());
        register(new AntiBlindModule());
        register(new TrueSightModule());
        register(new HealthTagsModule());
        register(new NameTagsModule());
        register(new XRayModule());
        register(new ChunkTrailsModule());
        register(new OxevyLogoModule());
        register(new ZoomModule());
        register(new ChamsModule());
        register(new PopChamsModule());
        register(new LogoutSpotsModule());
        register(new FreeLookModule());
        register(new BetterTabModule());
        register(new NoRenderModule());
        register(new ESPModule());
        register(new HandViewModule());
        register(new CameraTweaksModule());
        register(new BlockSelectionModule());
        register(new TunnelESPModule());
        register(new TrailModule());
        register(new EntityOwnerModule());
        register(new ItemPhysicsModule());
        register(new CosmeticsModule());

        // Set default enabled modules
        FpsHudModule fps = (FpsHudModule) getModuleByClass(FpsHudModule.class);
        if (fps != null) {
            fps.enable();
            fps.showMinMax.setValue(false);
            fps.showAverage.setValue(true);
            fps.showFrameTime.setValue(false);
            fps.showGraph.setValue(false);
            fps.performanceWarnings.setValue(false);
        }

        ArrayListHudModule arrayList = (ArrayListHudModule) getModuleByClass(ArrayListHudModule.class);
        if (arrayList != null) {
            arrayList.enable();
        }

        NotificationsModule notifs = (NotificationsModule) getModuleByClass(NotificationsModule.class);
        if (notifs != null) {
            notifs.enable();
        }

        KillAuraModule killAura = (KillAuraModule) getModuleByClass(KillAuraModule.class);
        if (killAura != null) {
            killAura.setBind(org.lwjgl.glfw.GLFW.GLFW_KEY_R);
        }

        FullbrightModule fullbright = (FullbrightModule) getModuleByClass(FullbrightModule.class);
        if (fullbright != null) {
            fullbright.enable();
        }

        WatermarkHudModule watermarkHud = (WatermarkHudModule) getModuleByClass(WatermarkHudModule.class);
        if (watermarkHud != null) {
            watermarkHud.enable();
        }

        CosmeticsModule cosmetics = (CosmeticsModule) getModuleByClass(CosmeticsModule.class);
        if (cosmetics != null) {
            cosmetics.enable();
        }

        MenuWatermark menuWatermark = (MenuWatermark) getModuleByClass(MenuWatermark.class);
        if (menuWatermark != null) {
            menuWatermark.enable();
        }

        LOGGER.info("Registered {} modules", modules.size());

        for (Module module : modules) {
            Oxevy.commandManager.register(new ModuleCommand(module));
        }

        Oxevy.configManager.addConfig(this);
    }

    public void register(Module module) {
        getModules().add(module);
        fastRegistry.put(module.getClass(), module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public Stream<Module> stream() {
        return getModules().stream();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModuleByClass(Class<T> clazz) {
        return (T) fastRegistry.get(clazz);
    }

    public Module getModuleByName(String name) {
        return stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public Module getModuleByDisplayName(String display) {
        return stream().filter(m -> m.getDisplayName().equalsIgnoreCase(display)).findFirst().orElse(null);
    }

    public List<Module> getModulesByCategory(Module.Category category) {
        return stream().filter(m -> m.getCategory() == category).toList();
    }

    public List<Module.Category> getCategories() {
        return Arrays.asList(Module.Category.values());
    }

    public void onLoad() {
        getModules().forEach(Module::onLoad);
    }

    public void onTick() {
        stream().filter(Feature::isEnabled).forEach(Module::onTick);
    }

    public void onRender2D(Render2DEvent event) {
        stream().filter(Feature::isEnabled).forEach(module -> module.onRender2D(event));
    }

    public void onRender3D(Render3DEvent event) {
        stream().filter(Feature::isEnabled).forEach(module -> module.onRender3D(event));
    }

    public void onUnload() {
        getModules().forEach(EVENT_BUS::unregister);
        getModules().forEach(Module::onUnload);
    }

    public void onKeyPressed(int key) {
        if (key <= 0) return;
        
        // Allow toggling ClickGui module even when screen is open
        for (Module module : getModules()) {
            if (module.getName().equals("ClickGui") && module.getBind().getKey() == key) {
                module.toggle();
                return;
            }
        }
        
        if (mc.screen != null) return;
        stream().filter(module -> module.getBind().getKey() == key).forEach(Module::toggle);
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        for (Module module : getModules()) {
            object.add(module.getName(), module.toJson());
        }
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        for (Module module : getModules()) {
            try {
                module.fromJson(object.get(module.getName()));
            } catch (Throwable throwable) {
                LOGGER.error("Failed to load module config for {}", module.getName(), throwable);
            }
        }
    }

    @Override
    public String getFileName() {
        return "modules.json";
    }
}
