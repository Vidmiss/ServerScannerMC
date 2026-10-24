package com.vidmiss.serverscanner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Queries a Minecraft server using the public Server List Ping protocol
 * (the same thing the multiplayer menu does), with a fallback to the
 * legacy (pre-1.7) ping. Returns everything the server tells us as JSON.
 */
public final class MinecraftPinger {

    private static final int READ_TIMEOUT_MS = 2500;
    private static final String S = "\u00a7"; // section sign

    private static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
    private static final String COLOR_CODES = "0123456789abcdef";

    private static final String[] SOFTWARE = {
            "Paper", "Spigot", "Purpur", "Pufferfish", "Folia", "Leaf", "Bukkit", "CraftBukkit", "Velocity",
            "BungeeCord", "Waterfall", "FlameCord", "Travertine", "Sponge", "Forge", "NeoForge", "Fabric",
            "Mohist", "Arclight", "Magma", "Cuberite", "Geyser", "Lilypad"};

    private MinecraftPinger() {}

    /** @return null if nothing is listening, otherwise a JSON description of the server. */
    public static JSONObject query(String host, int port, int connectTimeoutMs) {
        boolean[] reachable = {false};
        JSONObject result = null;

        try {
            result = modernPing(host, port, connectTimeoutMs, reachable);
        } catch (Exception ignored) {}

        if (result == null && reachable[0]) {
            try {
                result = legacyPing(host, port, connectTimeoutMs);
            } catch (Exception ignored) {}
        }

        if (result == null && reachable[0]) {
            result = base(host, port, "open", "none");
        }

        if (result != null) {
            try {
                result.put("foundAt", System.currentTimeMillis());
                String rdns = InetAddress.getByName(host).getCanonicalHostName();
                if (rdns != null && !rdns.equals(host)) result.put("hostname", rdns);
            } catch (Exception ignored) {}
        }
        return result;
    }

    // ---------------------------------------------------------------- modern

    private static JSONObject modernPing(String host, int port, int connectTimeoutMs, boolean[] reachable)
            throws Exception {
        try (Socket socket = new Socket()) {
            long t0 = System.nanoTime();
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            long connectMs = (System.nanoTime() - t0) / 1_000_000;
            reachable[0] = true;

            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            // Handshake (next state = status). Protocol -1 = "just tell me about yourself".
            ByteArrayOutputStream hsBuf = new ByteArrayOutputStream();
            DataOutputStream hs = new DataOutputStream(hsBuf);
            writeVarInt(hs, 0x00);
            writeVarInt(hs, -1);
            byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(hs, hostBytes.length);
            hs.write(hostBytes);
            hs.writeShort(port);
            writeVarInt(hs, 1);
            writeVarInt(out, hsBuf.size());
            out.write(hsBuf.toByteArray());

            // Status request
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            long reqTime = System.nanoTime();
            int packetLen = readVarInt(in);
            if (packetLen <= 0 || packetLen > 4 * 1024 * 1024) return null;
            int packetId = readVarInt(in);
            if (packetId != 0x00) return null;
            int strLen = readVarInt(in);
            if (strLen <= 0 || strLen > packetLen) return null;
            byte[] data = new byte[strLen];
            in.readFully(data);
            long statusMs = (System.nanoTime() - reqTime) / 1_000_000;

            JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));

            // Ping packet for a proper latency reading (optional; some servers skip it)
            long pingMs = -1;
            try {
                writeVarInt(out, 9);
                writeVarInt(out, 0x01);
                out.writeLong(System.currentTimeMillis());
                out.flush();
                long pt = System.nanoTime();
                readVarInt(in);
                if (readVarInt(in) == 0x01) {
                    in.readLong();
                    pingMs = (System.nanoTime() - pt) / 1_000_000;
                }
            } catch (Exception ignored) {}

            return parseModern(root, host, port, connectMs, statusMs, pingMs);
        }
    }

    private static JSONObject parseModern(JSONObject root, String host, int port,
                                          long connectMs, long statusMs, long pingMs) throws Exception {
        JSONObject o = base(host, port, "online", "modern");
        o.put("connectMs", connectMs);
        o.put("pingMs", pingMs >= 0 ? pingMs : statusMs);

        JSONObject ver = root.optJSONObject("version");
        if (ver != null) {
            o.put("version", ver.optString("name", ""));
            o.put("protocol", ver.optInt("protocol", -1));
        }

        JSONObject pl = root.optJSONObject("players");
        if (pl != null) {
            o.put("online", pl.optInt("online", -1));
            o.put("max", pl.optInt("max", -1));
            JSONArray sample = pl.optJSONArray("sample");
            JSONArray players = new JSONArray();
            if (sample != null) {
                for (int i = 0; i < sample.length() && i < 200; i++) {
                    JSONObject p = sample.optJSONObject(i);
                    if (p == null) continue;
                    JSONObject po = new JSONObject();
                    po.put("name", p.optString("name", ""));
                    po.put("id", p.optString("id", ""));
                    players.put(po);
                }
            }
            o.put("players", players);
        }

        if (root.has("description")) {
            StringBuilder sb = new StringBuilder();
            flatten(root.opt("description"), new Style(), sb);
            o.put("motd", sb.toString());
        }

        String favicon = root.optString("favicon", "");
        if (favicon.startsWith("data:image/png;base64,")) o.put("favicon", favicon);

        if (root.has("enforcesSecureChat")) o.put("enforcesSecureChat", root.optBoolean("enforcesSecureChat"));
        if (root.has("previewsChat")) o.put("previewsChat", root.optBoolean("previewsChat"));
        if (root.has("preventsChatReports")) o.put("preventsChatReports", root.optBoolean("preventsChatReports"));

        // Forge / modded info
        JSONArray mods = new JSONArray();
        String loader = null;
        JSONObject modinfo = root.optJSONObject("modinfo");
        if (modinfo != null) {
            loader = modinfo.optString("type", "modded");
            JSONArray list = modinfo.optJSONArray("modList");
            if (list != null) {
                for (int i = 0; i < list.length() && mods.length() < 300; i++) {
                    JSONObject m = list.optJSONObject(i);
                    if (m != null) mods.put((m.optString("modid", "?") + " " + m.optString("version", "")).trim());
                }
            }
        }
        JSONObject forgeData = root.optJSONObject("forgeData");
        if (forgeData != null) {
            loader = "forge";
            JSONArray list = forgeData.optJSONArray("mods");
            if (list != null) {
                for (int i = 0; i < list.length() && mods.length() < 300; i++) {
                    JSONObject m = list.optJSONObject(i);
                    if (m != null) mods.put(m.optString("modId", m.optString("modmarker", "?")));
                }
            }
        }
        JSONObject modpack = root.optJSONObject("modpackData");
        if (modpack != null) {
            o.put("modpack", (modpack.optString("name", "") + " " + modpack.optString("version", "")).trim());
        }
        if (loader != null) o.put("modLoader", loader);
        if (mods.length() > 0) o.put("mods", mods);

        String sw = guessSoftware(o.optString("version", ""));
        if (sw != null) o.put("software", sw);

        // Everything else the server sent, minus the (huge) favicon
        JSONObject raw = new JSONObject(root.toString());
        raw.remove("favicon");
        o.put("raw", raw.toString(2));
        return o;
    }

    // ---------------------------------------------------------------- legacy

    private static JSONObject legacyPing(String host, int port, int connectTimeoutMs) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            long t0 = System.nanoTime();
            out.write(new byte[]{(byte) 0xFE, 0x01});
            out.flush();
            if (in.read() != 0xFF) return null;
            int chars = in.readUnsignedShort();
            if (chars <= 0 || chars > 4096) return null;
            byte[] data = new byte[chars * 2];
            in.readFully(data);
            long ms = (System.nanoTime() - t0) / 1_000_000;

            String s = new String(data, StandardCharsets.UTF_16BE);
            JSONObject o = base(host, port, "online", "legacy");
            o.put("pingMs", ms);

            if (s.startsWith(S + "1\0")) {
                String[] p = s.split("\0");
                if (p.length >= 6) {
                    o.put("protocol", parseIntSafe(p[1]));
                    o.put("version", p[2]);
                    o.put("motd", p[3]);
                    o.put("online", parseIntSafe(p[4]));
                    o.put("max", parseIntSafe(p[5]));
                }
            } else {
                String[] p = s.split(S);
                if (p.length >= 3) {
                    o.put("motd", p[0]);
                    o.put("online", parseIntSafe(p[1]));
                    o.put("max", parseIntSafe(p[2]));
                    o.put("version", "Beta 1.8 \u2013 1.3 (legacy)");
                } else {
                    return null;
                }
            }
            String sw = guessSoftware(o.optString("version", ""));
            if (sw != null) o.put("software", sw);
            o.put("raw", s.replace("\0", "\\0"));
            return o;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static JSONObject base(String host, int port, String status, String type) {
        JSONObject o = new JSONObject();
        try {
            o.put("ip", host + ":" + port);
            o.put("host", host);
            o.put("port", port);
            o.put("status", status);
            o.put("pingType", type);
        } catch (Exception ignored) {}
        return o;
    }

    private static String guessSoftware(String versionName) {
        if (versionName == null) return null;
        String lower = versionName.toLowerCase();
        for (String s : SOFTWARE) {
            if (lower.contains(s.toLowerCase())) return s;
        }
        return null;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0, shift = 0, b;
        do {
            b = in.read();
            if (b == -1) throw new IOException("EOF");
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IOException("VarInt too big");
        } while ((b & 0x80) != 0);
        return result;
    }

    // ------------------------------------------------ chat component -> "§" text

    private static final class Style {
        String color;
        boolean bold, italic, underlined, strikethrough;

        Style copy() {
            Style s = new Style();
            s.color = color; s.bold = bold; s.italic = italic;
            s.underlined = underlined; s.strikethrough = strikethrough;
            return s;
        }

        void apply(JSONObject o) {
            if (o.has("color")) color = o.optString("color", null);
            if (o.has("bold")) bold = o.optBoolean("bold");
            if (o.has("italic")) italic = o.optBoolean("italic");
            if (o.has("underlined")) underlined = o.optBoolean("underlined");
            if (o.has("strikethrough")) strikethrough = o.optBoolean("strikethrough");
        }
    }

    private static void flatten(Object comp, Style parent, StringBuilder sb) {
        if (comp == null || comp == JSONObject.NULL) return;
        if (comp instanceof JSONArray) {
            JSONArray arr = (JSONArray) comp;
            for (int i = 0; i < arr.length(); i++) flatten(arr.opt(i), parent, sb);
        } else if (comp instanceof JSONObject) {
            JSONObject o = (JSONObject) comp;
            Style st = parent.copy();
            st.apply(o);
            String text = o.optString("text", "");
            if (text.isEmpty() && o.has("translate")) text = o.optString("translate", "");
            emit(sb, st, text);
            flatten(o.opt("extra"), st, sb);
        } else {
            emit(sb, parent, String.valueOf(comp));
        }
    }

    private static void emit(StringBuilder sb, Style st, String text) {
        if (text == null || text.isEmpty()) return;
        sb.append(S).append('r');
        if (st.color != null) {
            if (st.color.startsWith("#") && st.color.length() == 7) {
                sb.append(S).append('x');
                for (int i = 1; i < 7; i++) sb.append(S).append(st.color.charAt(i));
            } else {
                for (int i = 0; i < COLOR_NAMES.length; i++) {
                    if (COLOR_NAMES[i].equals(st.color)) {
                        sb.append(S).append(COLOR_CODES.charAt(i));
                        break;
                    }
                }
            }
        }
        if (st.bold) sb.append(S).append('l');
        if (st.italic) sb.append(S).append('o');
        if (st.underlined) sb.append(S).append('n');
        if (st.strikethrough) sb.append(S).append('m');
        sb.append(text);
    }
}
