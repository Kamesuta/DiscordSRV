/*-
 * LICENSE
 * DiscordSRV
 * -------------
 * Copyright (C) 2016 - 2021 Austin "Scarsz" Shapiro
 * -------------
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * END
 */

package github.scarsz.discordsrv.objects.managers.link;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;
import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PrettyUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class FileAccountLinkManager extends AbstractAccountLinkManager {
    private final BidiListMultimap linkedAccounts = new BidiListMultimap();

    private static class BidiListMultimap {
        public final ListMultimap<String, UUID> linkedAccounts = Multimaps.newListMultimap(new HashMap<>(), ArrayList::new);
        public final HashMap<UUID, String> linkedDiscords = new HashMap<>();

        public void clear() {
            linkedAccounts.clear();
            linkedDiscords.clear();
        }

        public void put(String discordId, UUID uuid) {
            linkedAccounts.put(discordId, uuid);
            linkedDiscords.put(uuid, discordId);
        }

        public void removeDiscord(UUID uuid) {
            linkedDiscords.remove(uuid);
            linkedAccounts.values().remove(uuid);
        }

        public void removeAccounts(String discordId) {
            linkedAccounts.removeAll(discordId);
            linkedDiscords.values().remove(discordId);
        }
    }

    @SuppressWarnings("ConstantConditions") // MalformedJsonException is a checked exception
    public FileAccountLinkManager() {
        if (!DiscordSRV.getPlugin().getLinkedAccountsFile().exists() ||
                DiscordSRV.getPlugin().getLinkedAccountsFile().length() == 0) return;
        linkedAccounts.clear();

        try {
            String fileContent = FileUtils.readFileToString(DiscordSRV.getPlugin().getLinkedAccountsFile(), StandardCharsets.UTF_8);
            if (fileContent == null || StringUtils.isBlank(fileContent)) fileContent = "{}";
            JsonObject jsonObject;
            try {
                jsonObject = DiscordSRV.getPlugin().getGson().fromJson(fileContent, JsonObject.class);
            } catch (Throwable t) {
                if (!(t instanceof MalformedJsonException) && !(t instanceof JsonSyntaxException) || !t.getMessage().contains("JsonPrimitive")) {
                    DiscordSRV.error("Failed to load linkedaccounts.json", t);
                    return;
                } else {
                    jsonObject = new JsonObject();
                }
            }

            jsonObject.entrySet().forEach(entry -> {
                String key = entry.getKey();
                if (key.isEmpty()) {
                    // empty values are not allowed.
                    return;
                }

                JsonElement entryValue = entry.getValue();
                List<String> values = !entryValue.isJsonArray()
                        ? Collections.singletonList(entryValue.getAsString())
                        : Streams.stream(entryValue.getAsJsonArray())
                        .map(JsonElement::getAsString)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                if (values.isEmpty()) {
                    // empty values are not allowed.
                    return;
                }

                try {
                    values.forEach(value -> linkedAccounts.put(key, UUID.fromString(value)));
                } catch (Exception e) {
                    try {
                        values.forEach(value -> linkedAccounts.put(value, UUID.fromString(key)));
                    } catch (Exception f) {
                        DiscordSRV.warning("Failed to load linkedaccounts.json file. It's extremely recommended to delete your linkedaccounts.json file.");
                    }
                }
            });
        } catch (IOException e) {
            DiscordSRV.error("Failed to load linkedaccounts.json", e);
        }
    }

    @Override
    public boolean isInCache(UUID uuid) {
        // always in cache
        return true;
    }

    @Override
    public boolean isInCache(String discordId) {
        // always in cache
        return true;
    }

    @Override
    public Multimap<String, UUID> getLinkedAccounts() {
        return linkedAccounts.linkedAccounts;
    }

    @Override
    public String getDiscordIdFromCache(UUID uuid) {
        return getDiscordId(uuid);
    }

    @Override
    public List<UUID> getUuidFromCache(String discordId) {
        return getUuid(discordId);
    }

    @Override
    public int getLinkedAccountCount() {
        return linkedAccounts.linkedAccounts.keys().size();
    }

    @Override
    public String process(String linkCode, String discordId) {
        // 複数アカウントをリンクする
        /*
        boolean contains;
        synchronized (linkedAccounts) {
            contains = linkedAccounts.linkedAccounts.containsKey(discordId);
        }
        if (contains) {
            if (DiscordSRV.config().getBoolean("MinecraftDiscordAccountLinkedAllowRelinkBySendingANewCode")) {
                unlink(discordId);
            } else {
                List<UUID> uuids;
                synchronized (linkedAccounts) {
                    uuids = linkedAccounts.linkedAccounts.get(discordId);
                }
                return uuids.stream().map(uuid -> {
                    OfflinePlayer offlinePlayer = DiscordSRV.getPlugin().getServer().getOfflinePlayer(uuid);
                    return LangUtil.Message.ALREADY_LINKED.toString()
                            .replace("%username%", PrettyUtil.beautifyUsername(offlinePlayer, "<Unknown>", false))
                            .replace("%uuids%", uuids.toString());
                }).collect(Collectors.joining("\n"));
            }
        }
        */

        // strip the code to get rid of non-numeric characters
        linkCode = linkCode.replaceAll("[^0-9]", "");

        if (linkingCodes.containsKey(linkCode)) {
            link(discordId, linkingCodes.get(linkCode));
            linkingCodes.remove(linkCode);

            return getUuid(discordId).stream()
                    .map(uuid -> {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                        if (player.isOnline()) {
                            MessageUtil.sendMessage(Bukkit.getPlayer(uuid), LangUtil.Message.MINECRAFT_ACCOUNT_LINKED.toString()
                                    .replace("%username%", DiscordUtil.getUserById(discordId).getName())
                                    .replace("%id%", DiscordUtil.getUserById(discordId).getId())
                            );
                        }

                        return LangUtil.Message.DISCORD_ACCOUNT_LINKED.toString()
                                .replace("%name%", PrettyUtil.beautifyUsername(player, "<Unknown>", false))
                                .replace("%displayname%", PrettyUtil.beautifyNickname(player, "<Unknown>", false))
                                .replace("%uuid%", uuid.toString());
                    }).collect(Collectors.joining("\n"));
        }

        return linkCode.length() == 4
                ? LangUtil.Message.UNKNOWN_CODE.toString()
                : LangUtil.Message.INVALID_CODE.toString();
    }

    @Override
    public String getDiscordId(UUID uuid) {
        synchronized (linkedAccounts) {
            return linkedAccounts.linkedDiscords.get(uuid);
        }
    }

    @Override
    public String getDiscordIdBypassCache(UUID uuid) {
        return getDiscordId(uuid);
    }

    @Override
    public Map<UUID, String> getManyDiscordIds(Set<UUID> uuids) {
        Map<UUID, String> results = new HashMap<>();
        for (UUID uuid : uuids) {
            String discordId;
            synchronized (linkedAccounts) {
                discordId = linkedAccounts.linkedDiscords.get(uuid);
            }
            if (discordId != null) results.put(uuid, discordId);
        }
        return results;
    }

    @Override
    public List<UUID> getUuid(String discordId) {
        synchronized (linkedAccounts) {
            return linkedAccounts.linkedAccounts.get(discordId);
        }
    }

    @Override
    public List<UUID> getUuidBypassCache(String discordId) {
        return getUuid(discordId);
    }

    @Override
    public Map<String, UUID> getManyUuids(Set<String> discordIds) {
        Map<String, UUID> results = new HashMap<>();
        for (String discordId : discordIds) {
            List<UUID> uuids;
            synchronized (linkedAccounts) {
                uuids = linkedAccounts.linkedAccounts.get(discordId);
            }
            uuids.forEach(uuid -> results.put(discordId, uuid));
        }
        return results;
    }

    @Override
    public void link(String discordId, UUID uuid) {
        if (discordId.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty discord id's are not allowed");
        }
        DiscordSRV.debug(Debug.ACCOUNT_LINKING, "File backed link: " + discordId + ": " + uuid);

        // Discord->Minecraftのリンクはそのまま
        //unlink(discordId);
        // make sure the user isn't linked
        unlink(uuid);

        synchronized (linkedAccounts) {
            linkedAccounts.put(discordId, uuid);
        }
        afterLink(discordId, uuid);
    }

    @Override
    public void unlink(UUID uuid) {
        String discordId;
        synchronized (linkedAccounts) {
            discordId = linkedAccounts.linkedDiscords.get(uuid);
        }
        if (discordId == null) return;

        synchronized (linkedAccounts) {
            beforeUnlink(Collections.singletonList(uuid), discordId);
            linkedAccounts.removeDiscord(uuid);
        }

        afterUnlink(Collections.singletonList(uuid), discordId);
    }

    @Override
    public void unlink(String discordId) {
        List<UUID> uuids;
        synchronized (linkedAccounts) {
            uuids = linkedAccounts.linkedAccounts.get(discordId);
        }
        if (uuids.isEmpty()) return;

        synchronized (linkedAccounts) {
            beforeUnlink(uuids, discordId);
            linkedAccounts.removeAccounts(discordId);
        }
        afterUnlink(uuids, discordId);
    }

    @Override
    public void save() {
        long startTime = System.currentTimeMillis();

        try {
            JsonObject map = new JsonObject();
            synchronized (linkedAccounts) {
                linkedAccounts.linkedAccounts.asMap().forEach((discordId, uuids) -> {
                    if (uuids.isEmpty()) return;

                    if (uuids.size() == 1) {
                        Optional<UUID> uuidOpt = uuids.stream().findFirst();
                        map.addProperty(discordId, uuidOpt.get().toString());
                        return;
                    }

                    JsonArray array = new JsonArray();
                    uuids.stream().map(String::valueOf).forEach(array::add);
                    map.add(discordId, array);
                });
            }
            FileUtils.writeStringToFile(DiscordSRV.getPlugin().getLinkedAccountsFile(), map.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DiscordSRV.error(LangUtil.InternalMessage.LINKED_ACCOUNTS_SAVE_FAILED + ": " + e.getMessage());
            return;
        }

        DiscordSRV.info(LangUtil.InternalMessage.LINKED_ACCOUNTS_SAVED.toString()
                .replace("{ms}", String.valueOf(System.currentTimeMillis() - startTime))
        );
    }

}
