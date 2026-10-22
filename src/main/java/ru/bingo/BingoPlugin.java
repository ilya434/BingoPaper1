package ru.bingo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class BingoPlugin extends JavaPlugin implements Listener {
    private static final int SIZE = 25;
    private static final int[] LINE_SLOTS = {
        0,1,2,3,4, 5,6,7,8,9, 10,11,12,13,14, 15,16,17,18,19, 20,21,22,23,24
    };
    private final Random random = new Random();
    private final Map<UUID, Set<Material>> progress = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<Material> card = new ArrayList<>();
    private final Set<UUID> roundWinners = new HashSet<>();
    private org.bukkit.scheduler.BukkitTask scanTask;
    private org.bukkit.scheduler.BukkitTask endTask;
    private boolean running = false;
    private long roundNumber = 0;
    private NamespacedKey cardKey;

    private final Material[] pool = {
        Material.OAK_LOG, Material.COBBLESTONE, Material.COAL, Material.IRON_INGOT, Material.COPPER_INGOT,
        Material.GOLD_INGOT, Material.REDSTONE, Material.LAPIS_LAZULI, Material.DIAMOND, Material.EMERALD,
        Material.STRING, Material.BONE, Material.GUNPOWDER, Material.LEATHER, Material.FEATHER,
        Material.WHEAT, Material.BREAD, Material.COOKED_BEEF, Material.COOKED_CHICKEN, Material.PAPER,
        Material.GLASS, Material.BRICK, Material.CLAY_BALL, Material.SAND, Material.GRAVEL,
        Material.FLINT, Material.ARROW, Material.BOW, Material.SHIELD, Material.FISHING_ROD,
        Material.CRAFTING_TABLE, Material.FURNACE, Material.CHEST, Material.BOOK, Material.COMPASS,
        Material.CLOCK, Material.BUCKET, Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.TORCH,
        Material.LANTERN, Material.BONE_MEAL, Material.SLIME_BALL, Material.HONEYCOMB, Material.OBSIDIAN,
        Material.QUARTZ, Material.AMETHYST_SHARD, Material.PRISMARINE_SHARD, Material.ENDER_PEARL,
        Material.BLAZE_ROD, Material.MAGMA_CREAM, Material.PHANTOM_MEMBRANE, Material.PUMPKIN,
        Material.MELON_SLICE, Material.APPLE, Material.GOLDEN_APPLE, Material.CARROT, Material.POTATO,
        Material.BEETROOT, Material.SUGAR, Material.CAKE, Material.COOKIE, Material.MAP,
        Material.SPYGLASS, Material.TRIDENT, Material.NAUTILUS_SHELL, Material.TURTLE_SCUTE
    };

    @Override public void onEnable() {
        saveDefaultConfig();
        cardKey = new NamespacedKey(this, "bingo_card_item");
        loadWinners();
        Bukkit.getPluginManager().registerEvents(this, this);
        long first = Math.max(1, getConfig().getLong("first-round-delay-seconds", 30)) * 20L;
        long interval = Math.max(1, getConfig().getLong("interval-minutes", 120)) * 60L * 20L;
        Bukkit.getScheduler().runTaskLater(this, this::startRound, first);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!running) startRound();
        }, first + interval, interval);
        scanTask = Bukkit.getScheduler().runTaskTimer(this, this::scanPlayers, 20L,
                Math.max(1, getConfig().getLong("scan-interval-ticks", 40)));
        getLogger().info("Bingo enabled. First round starts in " + (first / 20) + " seconds.");
    }

    @Override public void onDisable() {
        if (scanTask != null) scanTask.cancel();
        if (endTask != null) endTask.cancel();
        saveWinners();
    }

    private void startRound() {
        if (running) return;
        card.clear();
        List<Material> shuffled = new ArrayList<>(Arrays.asList(pool));
        Collections.shuffle(shuffled, random);
        card.addAll(shuffled.subList(0, SIZE));
        progress.clear();
        roundWinners.clear();
        running = true;
        roundNumber++;
        Bukkit.broadcastMessage(color("&6&lBINGO &eРаунд №" + roundNumber + " начался!"));
        Bukkit.broadcastMessage(color("&eСобирайте предметы из карточки &f/bingo &e. Первый собравший линию получит &b3 алмаза&e!"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            progress.put(player.getUniqueId(), new HashSet<>());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);
            openCard(player);
        }
        long minutes = getConfig().getLong("round-length-minutes", 60);
        if (minutes > 0) {
            if (endTask != null) endTask.cancel();
            endTask = Bukkit.getScheduler().runTaskLater(this, this::endRound, minutes * 60L * 20L);
        }
    }

    private void endRound() {
        if (!running) return;
        running = false;
        Bukkit.broadcastMessage(color("&6&lBINGO &7Раунд завершён. Новый раунд будет по расписанию."));
        progress.clear();
    }

    private void scanPlayers() {
        if (!running) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<Material> done = progress.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
            names.put(player.getUniqueId(), player.getName());
            for (Material required : card) {
                if (!done.contains(required) && playerHas(player, required)) {
                    done.add(required);
                }
            }
            if (hasLine(done)) declareWinner(player);
        }
    }

    private boolean playerHas(Player p, Material m) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == m && item.getAmount() > 0) return true;
        }
        return false;
    }

    private boolean hasLine(Set<Material> done) {
    for (int row = 0; row < 5; row++) {
        boolean ok = true;
        for (int col = 0; col < 5; col++) {
            if (!done.contains(card.get(row * 5 + col))) {
                ok = false;
                break;
            }
        }
        if (ok) return true;
    }

    for (int col = 0; col < 5; col++) {
        boolean ok = true;
        for (int row = 0; row < 5; row++) {
            if (!done.contains(card.get(row * 5 + col))) {
                ok = false;
                break;
            }
        }
        if (ok) return true;
    }

    boolean d1 = true;
    boolean d2 = true;
    for (int i = 0; i < 5; i++) {
        if (!done.contains(card.get(i * 5 + i))) d1 = false;
        if (!done.contains(card.get(i * 5 + (4 - i))) d2 = false;
    }

    return d1 || d2;
}
    private void declareWinner(Player p) {
        if (!running || !roundWinners.add(p.getUniqueId())) return;
        int reward = Math.max(0, getConfig().getInt("reward-diamonds", 3));
        Map<Integer, ItemStack> leftovers = p.getInventory().addItem(new ItemStack(Material.DIAMOND, reward));
        leftovers.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
        addWin(p);
        p.sendMessage(color("&aТы выиграл Bingo и получил &b" + reward + " алмаза&a!"));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.broadcastMessage(color("&6&lBINGO &e" + p.getName() + " собрал линию и победил!"));
        Bukkit.broadcastMessage(color("&7Победителей за всё время: &f" + totalWins() + " побед."));
        // The round remains active; other players can also complete a line and be rewarded.
    }

    private void openCard(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&6Bingo — раунд №" + roundNumber));
        Set<Material> done = progress.computeIfAbsent(p.getUniqueId(), id -> new HashSet<>());
        for (int i = 0; i < 25; i++) {
            Material material = card.get(i);
            ItemStack item = new ItemStack(done.contains(material) ? Material.LIME_STAINED_GLASS_PANE : material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color((done.contains(material) ? "&a✔ " : "&e") + pretty(material)));
            if (!done.contains(material)) meta.setLore(List.of(color("&7Добудь или получи этот предмет.")));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(color("&bКак играть"));
        im.setLore(List.of(color("&7Собери предметы в любой линии из 5 клеток."), color("&7Предметы должны быть в инвентаре."), color("&7Награда: 3 алмаза.")));
        info.setItemMeta(im);
        inv.setItem(26, info);
        p.openInventory(inv);
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith(color("&6Bingo — раунд №"))) event.setCancelled(true);
    }

    private String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : s.split(" ")) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        return out.toString().trim();
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private File winnersFile() { return new File(getDataFolder(), "winners.yml"); }

    private void loadWinners() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(winnersFile());
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                names.put(uuid, yaml.getString(key + ".name", "Unknown"));
                int count = yaml.getInt(key + ".wins", 0);
                if (count > 0) wins.put(uuid, count);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveWinners() {
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(winnersFile());
        for (Map.Entry<UUID, String> entry : names.entrySet()) yaml.set(entry.getKey() + ".name", entry.getValue());
        for (UUID uuid : knownWinIds()) {
            yaml.set(uuid.toString() + ".name", names.getOrDefault(uuid, "Unknown"));
            yaml.set(uuid.toString() + ".wins", wins.getOrDefault(uuid, 0));
        }
        try { yaml.save(winnersFile()); } catch (Exception e) { getLogger().severe("Cannot save winners.yml: " + e.getMessage()); }
    }

    private final Map<UUID, Integer> wins = new HashMap<>();

    private void addWin(Player p) {
        wins.merge(p.getUniqueId(), 1, Integer::sum);
        names.put(p.getUniqueId(), p.getName());
        saveWinners();
    }

    private Set<UUID> knownWinIds() { return new HashSet<>(wins.keySet()); }

    private int totalWins() { return wins.values().stream().mapToInt(Integer::intValue).sum(); }

    private List<Map.Entry<UUID, Integer>> ranked() {
        return wins.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).collect(Collectors.toList());
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("card")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Эта команда доступна только игроку."); return true; }
            if (!running) { p.sendMessage(color("&cСейчас Bingo не идёт. Следующий раунд начнётся автоматически.")); return true; }
            openCard(p); return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "winners" -> {
                sender.sendMessage(color("&6&lBINGO &eПобедители за всё время:"));
                List<Map.Entry<UUID, Integer>> list = ranked();
                if (list.isEmpty()) sender.sendMessage(color("&7Пока победителей нет."));
                for (int i = 0; i < Math.min(10, list.size()); i++) {
                    var e = list.get(i);
                    sender.sendMessage(color("&e" + (i + 1) + ". &f" + names.getOrDefault(e.getKey(), "Unknown") + " &7— &b" + e.getValue() + " побед"));
                }
                sender.sendMessage(color("&7Всего побед: &f" + totalWins()));
                return true;
            }
            case "stats" -> {
                if (args.length < 2) { sender.sendMessage(color("&eИспользование: /bingo stats <ник>")); return true; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                int count = wins.getOrDefault(target.getUniqueId(), 0);
                sender.sendMessage(color("&e" + args[1] + " &7— &b" + count + " побед в Bingo."));
                return true;
            }
            case "start" -> {
                if (!sender.hasPermission("bingo.admin")) { sender.sendMessage(color("&cНет прав.")); return true; }
                if (running) sender.sendMessage(color("&cРаунд уже идёт.")); else startRound();
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("bingo.admin")) { sender.sendMessage(color("&cНет прав.")); return true; }
                if (running) endRound(); else sender.sendMessage(color("&cРаунд не идёт."));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("bingo.admin")) { sender.sendMessage(color("&cНет прав.")); return true; }
                reloadConfig();
                sender.sendMessage(color("&aКонфигурация перезагружена. Интервал автозапуска обновится после перезапуска сервера."));
                return true;
            }
            default -> {
                sender.sendMessage(color("&e/bingo &7— открыть карточку"));
                sender.sendMessage(color("&e/bingo winners &7— список победителей"));
                sender.sendMessage(color("&e/bingo stats <ник> &7— статистика игрока"));
                if (sender.hasPermission("bingo.admin")) sender.sendMessage(color("&e/bingo start|stop|reload &7— управление"));
                return true;
            }
        }
    }
}
