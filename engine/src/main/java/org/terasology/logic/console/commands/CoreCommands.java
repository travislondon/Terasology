/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.logic.console.commands;

import com.google.common.base.Function;
import org.terasology.asset.AssetManager;
import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.asset.Assets;
import org.terasology.engine.GameEngine;
import org.terasology.engine.TerasologyConstants;
import org.terasology.engine.TerasologyEngine;
import org.terasology.engine.modes.StateLoading;
import org.terasology.engine.modes.StateMainMenu;
import org.terasology.engine.paths.PathManager;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.internal.EngineEntityManager;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.input.cameraTarget.CameraTargetSystem;
import org.terasology.logic.console.Console;
import org.terasology.logic.console.ConsoleColors;
import org.terasology.logic.console.Message;
import org.terasology.logic.console.commandSystem.ConsoleCommand;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.console.commandSystem.annotations.CommandParam;
import org.terasology.logic.console.suggesters.CommandNameSuggester;
import org.terasology.logic.health.DestroyEvent;
import org.terasology.logic.health.EngineDamageTypes;
import org.terasology.logic.health.HealthComponent;
import org.terasology.logic.inventory.PickupBuilder;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.permission.PermissionManager;
import org.terasology.math.Direction;
import org.terasology.math.geom.Quat4f;
import org.terasology.math.geom.Vector3f;
import org.terasology.naming.Name;
import org.terasology.network.ClientComponent;
import org.terasology.network.JoinStatus;
import org.terasology.network.NetworkMode;
import org.terasology.network.NetworkSystem;
import org.terasology.persistence.WorldDumper;
import org.terasology.persistence.serializers.PrefabSerializer;
import org.terasology.registry.CoreRegistry;
import org.terasology.registry.In;
import org.terasology.rendering.FontColor;
import org.terasology.rendering.assets.material.MaterialData;
import org.terasology.rendering.assets.shader.ShaderData;
import org.terasology.rendering.cameras.Camera;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.asset.UIData;
import org.terasology.rendering.nui.layers.mainMenu.MessagePopup;
import org.terasology.rendering.nui.layers.mainMenu.WaitPopup;
import org.terasology.rendering.nui.skin.UISkinData;
import org.terasology.rendering.world.WorldRenderer;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.family.BlockFamily;
import org.terasology.world.block.items.BlockItemFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * @author Immortius
 */
@RegisterSystem
public class CoreCommands extends BaseComponentSystem {

    @In
    private EntityManager entityManager;

    @In
    private CameraTargetSystem cameraTargetSystem;

    @In
    private WorldRenderer worldRenderer;

    @In
    private PrefabManager prefabManager;

    @In
    private BlockManager blockManager;

    @In
    private Console console;

    private PickupBuilder pickupBuilder;

    @Override
    public void initialise() {
        pickupBuilder = new PickupBuilder(entityManager);
    }

    @Command(shortDescription = "Reloads a skin")
    public String reloadSkin(@CommandParam("skin") String skin) {
        AssetUri uri = new AssetUri(AssetType.UI_SKIN, skin);
        UISkinData uiSkinData = CoreRegistry.get(AssetManager.class).loadAssetData(uri, UISkinData.class);
        if (uiSkinData != null) {
            CoreRegistry.get(AssetManager.class).generateAsset(uri, uiSkinData);
            return "Success";
        } else {
            return "Unable to resolve skin '" + skin + "'";
        }
    }

    @Command(shortDescription = "Enables the automatic reloading of screens when their file changes")
    public String enableAutoScreenReloading() {
        CoreRegistry.get(NUIManager.class).enableAutoReload();
        return "Automatic reloading of screens enabled: Check console for hints where they get loaded from";
    }

    @Command(shortDescription = "Reloads a ui and clears the HUD. Use at your own risk")
    public String reloadUI(@CommandParam("ui") String ui) {
        CoreRegistry.get(NUIManager.class).clear();

        AssetUri uri = new AssetUri(AssetType.UI_ELEMENT, ui);
        UIData uiData = CoreRegistry.get(AssetManager.class).loadAssetData(uri, UIData.class);
        if (uiData != null) {
            CoreRegistry.get(AssetManager.class).generateAsset(uri, uiData);
            return "Success";
        } else {
            return "Unable to resolve ui '" + ui + "'";
        }
    }

    @Command(shortDescription = "Reloads a shader")
    public String reloadShader(@CommandParam("shader") String shader) {
        AssetUri uri = new AssetUri(AssetType.SHADER, shader);
        ShaderData shaderData = CoreRegistry.get(AssetManager.class).loadAssetData(uri, ShaderData.class);
        if (shaderData != null) {
            CoreRegistry.get(AssetManager.class).generateAsset(uri, shaderData);
            return "Success";
        } else {
            return "Unable to resolve shader '" + shader + "'";
        }
    }

    @Command(shortDescription = "Reloads a material")
    public String reloadMaterial(@CommandParam("material") String material) {
        AssetUri uri = new AssetUri(AssetType.MATERIAL, material);
        MaterialData materialData = CoreRegistry.get(AssetManager.class).loadAssetData(uri, MaterialData.class);
        if (materialData != null) {
            CoreRegistry.get(AssetManager.class).generateAsset(uri, materialData);
            return "Success";
        } else {
            return "Unable to resolve material '" + material + "'";
        }
    }

    @Command(shortDescription = "Toggles Fullscreen Mode", requiredPermission = PermissionManager.NO_PERMISSION)
    public String fullscreen() {
        TerasologyEngine te = (TerasologyEngine) CoreRegistry.get(GameEngine.class);

        te.setFullscreen(!te.isFullscreen());

        if (te.isFullscreen()) {
            return "Switched to fullscreen mode";
        } else {
            return "Switched to windowed mode";
        }

    }

    @Command(shortDescription = "Removes all entities of the given prefab", runOnServer = true)
    public void destroyEntitiesUsingPrefab(@CommandParam("prefabName") String prefabName) {
        Prefab prefab = entityManager.getPrefabManager().getPrefab(prefabName);
        if (prefab != null) {
            for (EntityRef entity : entityManager.getAllEntities()) {
                if (prefab.getURI().equals(entity.getPrefabURI())) {
                    entity.destroy();
                }
            }
        }
    }

    @Command(shortDescription = "Exits the game", requiredPermission = PermissionManager.NO_PERMISSION)
    public void exit() {
        CoreRegistry.get(GameEngine.class).shutdown();
    }

    @Command(shortDescription = "Join a game", requiredPermission = PermissionManager.NO_PERMISSION)
    public void join(@CommandParam("address") final String address, @CommandParam(value = "port", required = false) Integer portParam) {
        final int port = portParam != null ? portParam : TerasologyConstants.DEFAULT_PORT;

        Callable<JoinStatus> operation = new Callable<JoinStatus>() {

            @Override
            public JoinStatus call() throws InterruptedException {
                NetworkSystem networkSystem = CoreRegistry.get(NetworkSystem.class);
                JoinStatus joinStatus = networkSystem.join(address, port);
                return joinStatus;
            }
        };

        final NUIManager manager = CoreRegistry.get(NUIManager.class);
        final WaitPopup<JoinStatus> popup = manager.pushScreen(WaitPopup.ASSET_URI, WaitPopup.class);
        popup.setMessage("Join Game", "Connecting to '" + address + ":" + port + "' - please wait ...");
        popup.onSuccess(new Function<JoinStatus, Void>() {

            @Override
            public Void apply(JoinStatus result) {
                GameEngine engine = CoreRegistry.get(GameEngine.class);
                if (result.getStatus() != JoinStatus.Status.FAILED) {
                    engine.changeState(new StateLoading(result));
                } else {
                    MessagePopup screen = manager.pushScreen(MessagePopup.ASSET_URI, MessagePopup.class);
                    screen.setMessage("Failed to Join", "Could not connect to server - " + result.getErrorMessage());
                }

                return null;
            }
        });
        popup.startOperation(operation, true);
    }

    @Command(shortDescription = "Leaves the current game and returns to main menu",
            requiredPermission = PermissionManager.NO_PERMISSION)
    public String leave() {
        NetworkSystem networkSystem = CoreRegistry.get(NetworkSystem.class);
        if (networkSystem.getMode() != NetworkMode.NONE) {
            CoreRegistry.get(GameEngine.class).changeState(new StateMainMenu());
            return "Leaving..";
        } else {
            return "Not connected";
        }
    }

    @Command(shortDescription = "Writes out information on all entities to a text file for debugging",
            helpText = "Writes entity information out into a file named \"entityDump.txt\".")
    public void dumpEntities() throws IOException {
        EngineEntityManager engineEntityManager = (EngineEntityManager) entityManager;
        PrefabSerializer prefabSerializer = new PrefabSerializer(engineEntityManager.getComponentLibrary(), engineEntityManager.getTypeSerializerLibrary());
        WorldDumper worldDumper = new WorldDumper(engineEntityManager, prefabSerializer);
        worldDumper.save(PathManager.getInstance().getHomePath().resolve("entityDump.txt"));
    }

    // TODO: Fix this up for multiplayer (cannot at the moment due to the use of the camera)
    @Command(shortDescription = "Spawns an instance of a prefab in the world")
    public String spawnPrefab(@CommandParam("prefabId") String prefabName) {
        Camera camera = worldRenderer.getActiveCamera();
        Vector3f spawnPos = camera.getPosition();
        Vector3f offset = new Vector3f(camera.getViewingDirection());
        offset.scale(2);
        spawnPos.add(offset);
        Vector3f dir = new Vector3f(camera.getViewingDirection());
        dir.y = 0;
        if (dir.lengthSquared() > 0.001f) {
            dir.normalize();
        } else {
            dir.set(Direction.FORWARD.getVector3f());
        }
        Quat4f rotation = Quat4f.shortestArcQuat(Direction.FORWARD.getVector3f(), dir);

        Prefab prefab = Assets.getPrefab(prefabName);
        if (prefab != null && prefab.getComponent(LocationComponent.class) != null) {
            entityManager.create(prefab, spawnPos, rotation);
            return "Done";
        } else if (prefab == null) {
            return "Unknown prefab";
        } else {
            return "Prefab cannot be spawned (no location component)";
        }
    }

    // TODO: Fix this up for multiplayer (cannot at the moment due to the use of the camera), also applied required
    // TODO: permission
    @Command(shortDescription = "Spawns a block in front of the player", helpText = "Spawns the specified block as a " +
            "item in front of the player. You can simply pick it up.")
    public String spawnBlock(@CommandParam("blockName") String blockName) {
        Camera camera = worldRenderer.getActiveCamera();
        Vector3f spawnPos = camera.getPosition();
        Vector3f offset = camera.getViewingDirection();
        offset.scale(3);
        spawnPos.add(offset);

        BlockFamily block = blockManager.getBlockFamily(blockName);
        if (block == null) {
            return "";
        }

        BlockItemFactory blockItemFactory = new BlockItemFactory(entityManager);
        EntityRef blockItem = blockItemFactory.newInstance(block);

        pickupBuilder.createPickupFor(blockItem, spawnPos, 60);
        return "Spawned block.";
    }

    @Command(shortDescription = "Prints out short descriptions for all available commands, or a longer help text if a command is provided.",
            requiredPermission = PermissionManager.NO_PERMISSION)
    public String help(@CommandParam(value = "command", required = false, suggester = CommandNameSuggester.class) Name commandName) {
        if (commandName == null) {
            StringBuilder msg = new StringBuilder();
            Collection<ConsoleCommand> commands = console.getCommands();

            for (ConsoleCommand cmd : commands) {
                if (!msg.toString().isEmpty()) {
                    msg.append(Message.NEW_LINE);
                }

                msg.append(FontColor.getColored(cmd.getUsage(), ConsoleColors.COMMAND));
                msg.append(" - ");
                msg.append(cmd.getDescription());
            }

            return msg.toString();
        } else {
            ConsoleCommand cmd = console.getCommand(commandName);
            if (cmd == null) {
                return "No help available for command '" + commandName + "'. Unknown command.";
            } else {
                StringBuilder msg = new StringBuilder();

                msg.append("=====================================================================================================================");
                msg.append(Message.NEW_LINE);
                msg.append(cmd.getUsage());
                msg.append(Message.NEW_LINE);
                msg.append("=====================================================================================================================");
                msg.append(Message.NEW_LINE);
                if (!cmd.getHelpText().isEmpty()) {
                    msg.append(cmd.getHelpText());
                    msg.append(Message.NEW_LINE);
                    msg.append("=====================================================================================================================");
                    msg.append(Message.NEW_LINE);
                } else if (!cmd.getDescription().isEmpty()) {
                    msg.append(cmd.getDescription());
                    msg.append(Message.NEW_LINE);
                    msg.append("=====================================================================================================================");
                    msg.append(Message.NEW_LINE);
                }

                return msg.toString();
            }
        }
    }
}
