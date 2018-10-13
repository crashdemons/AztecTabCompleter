package com.github.crashdemons.aztectabcompleter;


import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.brigadier.tree.CommandNode;


import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author crash
 */
public class AZTabPlugin extends JavaPlugin implements Listener {
    private ProtocolManager protocolManager;
    public volatile boolean enabled = false;
    private volatile HashSet<String> visibleCommands;

    public AZTabPlugin() {
        this.visibleCommands = new HashSet<>();
    }
    
    private void log(String s){
        getLogger().info(s);
    }
    
    private void loadConfig(){
        saveDefaultConfig();//fails silently if config exists
        reloadConfig();
        visibleCommands = new HashSet<>( getConfig().getStringList("visible-commands") );
    }
    
    
    // Fired when plugin is disabled
    @Override
    public void onDisable() {
        log("Disabling...");
        enabled=false;
        log("Disabed.");
    }
    
    @Override
    public void onEnable() {
        log("Enabling... v"+this.getDescription().getVersion());
        loadConfig();
        log("Loaded "+visibleCommands.size()+" visible commands.");
        getServer().getPluginManager().registerEvents(this, this);
        protocolManager = ProtocolLibrary.getProtocolManager();
        createInitialCommandsFilter();
        //createTabCompleteOverride();
        enabled=true;
        log("Enabled.");
    }
    
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("aztabreload")) {
            if(!sender.hasPermission("aztectabcompleter.reload")){ sender.sendMessage("You don't have permission to do this."); return true; }
            loadConfig();
            sender.sendMessage("[AZTab] Config reloaded with "+visibleCommands.size()+" commands.");
            return true;
        }
        return false;
    }

    private void createInitialCommandsFilter(){
        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.HIGHEST, new PacketType[] { PacketType.Play.Server.COMMANDS }) {

        @Override
        public void onPacketSending (PacketEvent event) {
            AZTabPlugin pl = (AZTabPlugin) this.plugin;
            
            
            Player playerDestination = event.getPlayer();
            if(playerDestination==null) return;
            if(playerDestination.hasPermission("aztectabcompleter.bypass")) return;
            
            
            
            if(!pl.enabled) return;
            pl.log("Intercepted Commands packet, filtering...");
            
            //the new Commands packet syntax contains a RootNode object containing multiple CommandNode objects inside in the form of a list
            //CommandNode is difficult to construct, so instead we just selectively remove them from the collection.
            PacketContainer epacket = event.getPacket();//get the outgoing spigot packet containing the command list
            RootCommandNode rcn = epacket.getSpecificModifier(RootCommandNode.class).read(0);//get the Root object
            //this.plugin.getLogger().info("RCN Name: "+rcn.getName());
            //this.plugin.getLogger().info("RCN Usage: "+rcn.getUsageText());
            @SuppressWarnings("unchecked")
            Collection<CommandNode<Object>> children = rcn.getChildren();
            //this.plugin.getLogger().info("RCN Children: "+children.size());
            Iterator<CommandNode<Object>> iterator = children.iterator();
            while (iterator.hasNext()) {
                CommandNode<Object> cn = iterator.next();
                //this.plugin.getLogger().info("   CN Name: "+cn.getName());
                //this.plugin.getLogger().info("   CN Usage: "+cn.getUsageText());
                if( ! visibleCommands.contains(cn.getName()) )
                    iterator.remove();
            }
           
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.COMMANDS);
            packet.getSpecificModifier(RootCommandNode.class).write(0, rcn);//write the modified root object into a new packet
            try{
                try{
                    ProtocolLibrary.getProtocolManager().sendServerPacket(playerDestination, packet, false);//send packet - disable further filtering.
                }catch(IllegalArgumentException e){
                    String name = playerDestination.getName();
                    if(name==null) name = "[null]";
                    pl.log("Problem sending packet to " + name +" "+playerDestination.getUniqueId());
                }
            }catch(InvocationTargetException e){
                e.printStackTrace();
            }
            event.setCancelled(true);//prevent default tabcomplete
            
        }

    });
    }
    
}