package com.juanegameryt.jtunliveanddeath;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LiveAndDeath extends JavaPlugin implements Listener {

    private Map<String, Integer> fragmentCountMap = new HashMap<>();
    private int maxX;
    private int minX;
    private int maxZ;
    private int minZ;
    private Location lastEmeraldLocation;
    private String locale;

    @Override
    public void onEnable() {
        try {
            getLogger().info("[JTunCraft] El plugin LivesAndDeath se ha activado! ¡Suerte con la muerte!");
            getServer().getPluginManager().registerEvents(this, this);
            getCommand("liveanddeath").setExecutor(this);
            getCommand("ldd").setExecutor(this);

            // Iniciar la generación aleatoria de menas de esmeralda con valores predeterminados
            loadConfigurations();
            startRandomEmeraldOreGeneration();

            // Buscar el plugin "Multiverse Core"
            Plugin multiverseCorePlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
            if (multiverseCorePlugin != null && multiverseCorePlugin.isEnabled()) {
                getLogger().info("¡Se encontró y registró Multiverse Core!");

                // Verificar la existencia del mundo "desterrado" en Multiverse Core
                if (Bukkit.getWorld("desterrado") == null) {
                    getLogger().info("El mundo 'desterrado' no existe. Creando el mundo...");
                    getServer().dispatchCommand(getServer().getConsoleSender(), "mv create desterrado nether");
                }
            } else {
                getLogger().warning("No se encontró o no está habilitado Multiverse Core. Algunas funciones pueden no estar disponibles.");
            }

            // Cargar el locale desde config.yml
            this.locale = getConfig().getString("locale", "en").toLowerCase();

            // Verificar y cargar el archivo de mensajes correspondiente al locale
            File messagesFile = new File(getDataFolder(), locale + ".yml");
            if (!messagesFile.exists()) {
                saveResource(locale + ".yml", false);
            }

            // Comprobar PlaceholderAPI e Inicializarlo
            Plugin placeholderAPIPlugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (placeholderAPIPlugin != null && placeholderAPIPlugin.isEnabled()) {
                new LivesAndDeathExpansion(this).register();
                getLogger().info("¡Se encontró y registró PlaceholderAPI!");
            } else {
                getLogger().warning("No se encontró o no está habilitado PlaceholderAPI. La expansión no se registrará.");
            }

        } catch (Exception e) {
            getLogger().severe("Error during plugin initialization: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }


    private void loadConfigurations() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        // Cargar las configuraciones
        int minZ = getConfig().getInt("coord_z.min", 0);
        int maxZ = getConfig().getInt("coord_z.max", 100);
        int minX = getConfig().getInt("coord_x.min", 0);
        int maxX = getConfig().getInt("coord_x.max", 100);

        // Asegurarse de que los valores sean válidos y ajustarlos según sea necesario
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
    }

    @EventHandler   
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Verificar si el jugador está en el mundo desterrado
        if (player.getWorld().getName().equalsIgnoreCase("desterrado")) {
        // Eliminar fragmentos recolectados
            fragmentCountMap.put(player.getName(), 0);

        // Teleportar al jugador de vuelta al mundo desterrado
            player.performCommand("mv tp " + player.getName() + " desterrado");

        // Mostrar el título indicando la pérdida de fragmentos
            sendTitle(player, ChatColor.BOLD + "" + ChatColor.RED + "¡Has perdido!", ChatColor.RESET + "Los fragmentos se reiniciaron, suerte la próxima.");
        } else {
        // Retrasar la ejecución de la ruleta por 5 segundos
            new BukkitRunnable() {
                @Override
                public void run() {
                // Generar un número aleatorio entre 0 y 1
                    double randomValue = Math.random();

                // Probabilidad de salvar al jugador (ajústala según tus necesidades)
                    double saveProbability = 0.5; // 50% de probabilidad de salvar al jugador

                // Comprobar si el valor aleatorio está por debajo de la probabilidad de salvar
                    if (randomValue < saveProbability) {
                        sendTitle(player, ChatColor.BOLD + "" + ChatColor.RED + "Salvado", ChatColor.RESET + "¡Te encuentras a salvo!");
                    // Poner al jugador en modo supervivencia
                        player.setGameMode(GameMode.SURVIVAL);
                    } else {
                    sendTitle(player, ChatColor.BOLD + "" + ChatColor.DARK_PURPLE + "Inframundo", ChatColor.RESET + "¡Has sido desterrado!");
                    // Poner al jugador en el mundo desterrado
                    teleportToDesterrado(player);
                }
            }
        }.runTaskLater(this, 5 * 20); // 20 ticks por segundo, 5 segundos de retraso
    }
}

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Restablecer el contador de fragmentos al reaparecer
        fragmentCountMap.put(player.getName(), 0);
        // Mostrar el título al reaparecer
        sendTitle(player, ChatColor.GREEN + "¡Bienvenido de vuelta!", "");
        // Puedes realizar acciones adicionales al reaparecer, si es necesario
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (event.getBlock().getType() == Material.EMERALD_ORE &&
                event.getBlock().getWorld().getName().equalsIgnoreCase("desterrado")) {
            int count = fragmentCountMap.getOrDefault(player.getName(), 0) + 1;
            fragmentCountMap.put(player.getName(), count);

            player.sendMessage(ChatColor.GREEN + "Fragmentos: " + count + "/8");

            if (count >= 8) {
                // Teletransportar al jugador al mundo principal usando Multiverse Core
                player.performCommand("mv tp " + player.getName() + " world");
                // Establecer spawnpoint en el mundo principal usando Multiverse Core
                player.performCommand("lp user " + player.getName() + " parent set default");
                // Reiniciar el contador de fragmentos
                fragmentCountMap.put(player.getName(), 0);
                player.sendMessage(ChatColor.GREEN + "¡Has recolectado suficientes fragmentos! Teletransportándote al mundo principal.");
            } else {
                // Simular el efecto de recolección de esmeralda
                event.setCancelled(true);
                event.getBlock().setType(Material.AIR);
                player.getInventory().addItem(new ItemStack(Material.EMERALD));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }

    private void sendTitle(Player player, String title, String subtitle) {
        player.sendTitle(title, subtitle, 20, 40, 20);
    }

    private void teleportToDesterrado(Player player) {
        // Reemplaza "desterrado" con el nombre correcto del mundo desterrado en Multiverse Core
        player.performCommand("mv tp " + player.getName() + " desterrado");
        player.performCommand("lp user " + player.getName() + " parent set desterradk");
    }

    private void startRandomEmeraldOreGeneration() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World desterradoWorld = Bukkit.getWorld("desterrado");
                if (desterradoWorld != null) {
                    Random random = new Random();
                    int x = random.nextInt(maxX - minX + 1) + minX;
                    int y = random.nextInt(105) + 16; // Rango de 16 a 120
                    int z = random.nextInt(maxZ - minZ + 1) + minZ;

                    lastEmeraldLocation = new Location(desterradoWorld, x, y, z);

                    desterradoWorld.getBlockAt(x, y, z).setType(Material.EMERALD_ORE);
                    // Notificar la generación del bloque al jugador en el chat
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer.getWorld().getName().equalsIgnoreCase("desterrado")) {
                            onlinePlayer.sendMessage(ChatColor.GREEN + "Se ha generado una mena de esmeralda en las coordenadas: " +
                                    "(" + x + ", " + y + ", " + z + ")");
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0, 5 * 60 * 20); // 20 ticks por segundo, 5 minutos entre generaciones
    }

    // Clase de expansión de PlaceholderAPI
    public class LivesAndDeathExpansion extends PlaceholderExpansion {

        private final LiveAndDeath plugin;
    
        public LivesAndDeathExpansion(LiveAndDeath plugin) {
            this.plugin = plugin;
        }
    
        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            if (player == null) {
                return null;
            }
    
            if (identifier.equals("fragmentos_necesarios")) {
                // Definir la cantidad necesaria de fragmentos (en este caso, 8)
                return "8";
            }
    
            if (identifier.equals("fragmentos_conseguidos")) {
                // Obtener la cantidad de fragmentos que ha recolectado el jugador
                return String.valueOf(plugin.fragmentCountMap.getOrDefault(player.getName(), 0));
            }
    
            if (identifier.equals("players_desterrados")) {
                // Obtener la cantidad de jugadores en el mundo desterrado
                return String.valueOf(Bukkit.getWorld("desterrado").getPlayers().size());
            }
    
            if (identifier.equals("ultima_mena")) {
                // Obtener las coordenadas de la última mena generada
                if (plugin.lastEmeraldLocation != null) {
                    return plugin.lastEmeraldLocation.getBlockX() + " " +
                            plugin.lastEmeraldLocation.getBlockY() + " " +
                            plugin.lastEmeraldLocation.getBlockZ();
                } else {
                    return "N/A";
                }
            }
    
            return null;
        }
    
        @Override
        public String getAuthor() {
            return "JuanEGamerYT";
        }
    
        @Override
        public String getIdentifier() {
            return "liveanddeath";
        }
    
        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }
    }
    

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("add")) {
                if (args.length > 2) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null) {
                        try {
                            int count = Integer.parseInt(args[2]);
                            int currentCount = fragmentCountMap.getOrDefault(target.getName(), 0);
                            fragmentCountMap.put(target.getName(), currentCount + count);
                            sender.sendMessage(ChatColor.GREEN + "Fragmentos añadidos a " + target.getName() + ": " + count);
                            target.sendMessage(ChatColor.GREEN + "Se te han añadido fragmentos: " + count);
                            return true;
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Error: La entrada debe ser un número entero.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Error: Jugador no encontrado.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Error: Uso incorrecto. /liveanddeath add (jugador) (cantidad)");
                }
            } else if (args[0].equalsIgnoreCase("remove")) {
                if (args.length > 2) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null) {
                        try {
                            int count = Integer.parseInt(args[2]);
                            int currentCount = fragmentCountMap.getOrDefault(target.getName(), 0);
                            int newCount = Math.max(0, currentCount - count);
                            fragmentCountMap.put(target.getName(), newCount);
                            sender.sendMessage(ChatColor.GREEN + "Fragmentos eliminados a " + target.getName() + ": " + count);
                            target.sendMessage(ChatColor.GREEN + "Se te han eliminado fragmentos: " + count);
                            return true;
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Error: La entrada debe ser un número entero.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Error: Jugador no encontrado.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Error: Uso incorrecto. /liveanddeath remove (jugador) (cantidad)");
                }
            } else if (args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(ChatColor.GREEN + "Comandos de LiveAndDeath:");
                sender.sendMessage(ChatColor.GREEN + "/liveanddeath and /ldd add (jugador) (cantidad) - Añade fragmentos a un jugador.");
                sender.sendMessage(ChatColor.GREEN + "/liveanddeath and /ldd remove (jugador) (cantidad) - Elimina fragmentos a un jugador.");
                return true;
            }
        }
        sender.sendMessage(ChatColor.RED + "Error: Comando desconocido o incorrecto. Usa /liveanddeath help para obtener ayuda.");
        return true;
    }

}