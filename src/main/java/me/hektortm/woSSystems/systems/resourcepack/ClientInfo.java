package me.hektortm.woSSystems.systems.resourcepack;

import java.lang.reflect.Method;
import java.util.UUID;

/** What the server knows about a player's client, from ViaVersion and Floodgate when they are installed. */
public interface ClientInfo {

    /** The client's protocol version, or -1 when unknown (ViaVersion absent or any error). */
    int protocol(UUID player);

    /** True for a Bedrock player (Floodgate); false when Floodgate is absent. */
    boolean bedrock(UUID player);

    /** Both plugins are reached by reflection so neither is a build dependency. */
    static ClientInfo reflective() {
        return new ClientInfo() {
            @Override
            public int protocol(UUID player) {
                try {
                    Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
                    Object api = via.getMethod("getAPI").invoke(null);
                    Method m = api.getClass().getMethod("getPlayerProtocolVersion", UUID.class);
                    m.setAccessible(true);
                    Object version = m.invoke(api, player);
                    Method get = version.getClass().getMethod("getVersion");
                    get.setAccessible(true);
                    return (Integer) get.invoke(version);
                } catch (Throwable t) {
                    return -1;
                }
            }

            @Override
            public boolean bedrock(UUID player) {
                try {
                    Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    Object instance = api.getMethod("getInstance").invoke(null);
                    return (Boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player);
                } catch (Throwable t) {
                    return false;
                }
            }
        };
    }
}
