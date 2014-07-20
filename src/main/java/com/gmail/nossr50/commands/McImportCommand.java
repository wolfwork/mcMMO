package com.gmail.nossr50.commands;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.gmail.nossr50.mcMMO;

public class McImportCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (args.length) {
            case 0:
                importAllModConfig();
                return true;

            default:
                return false;
        }
    }

    public boolean importAllModConfig() {
        return (importModConfig("blocks", true) && importModConfig("tools", true) && importModConfig("armor", true));
    }

    public boolean importModConfig(String type, boolean importAll) {
        String importFilePath = mcMMO.getModDirectory() + File.separator + "import";
        File importFile = new File(importFilePath, type + ".yml");
        mcMMO.p.getLogger().info("Starting import of " + importFile.getName());

        HashMap<String, ArrayList<String>> materialNames = new HashMap<String, ArrayList<String>>();

        BufferedReader in = null;

        try {
            // Open the file
            in = new BufferedReader(new FileReader(importFile));

            String line;
            String materialName;

            // While not at the end of the file
            while ((line = in.readLine()) != null) {
                // Read the line in and copy it to the output it's not the player we want to edit

                String[] split1 = line.split("material ");

                if (split1.length != 2) {
                    continue;
                }

                String[] split2 = split1[1].split(" with");

                if (split2.length != 2) {
                    continue;
                }

                materialName = split2[0];
                String[] materialSplit = materialName.split("_");

                if (materialSplit.length > 1) {
                    String modName = materialSplit[0].toLowerCase();
                    if (!materialNames.containsKey(modName)) {
                        materialNames.put(modName, new ArrayList<String>());
                    }

                    materialNames.get(modName).add(materialName);
                    continue;
                }

                if (!materialNames.containsKey("UNKNOWN")) {
                    materialNames.put("UNKNOWN", new ArrayList<String>());
                }

                materialNames.get("UNKNOWN").add(materialName);
            }
        }
        catch (FileNotFoundException e) {
            if (!importAll) {
                mcMMO.p.getLogger().warning("Could not find " + importFile.getAbsolutePath() + " ! (No such file or directory)");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        finally {
            tryClose(in);
        }

        createOutput(type, materialNames);

        return true;
    }

    private void tryClose(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createOutput(String type, HashMap<String, ArrayList<String>> materialNames) {
        File outputFilePath = new File(mcMMO.getModDirectory() + File.separator + "output");
        if (!outputFilePath.exists() && !outputFilePath.mkdirs()) {
            mcMMO.p.getLogger().severe("Could not create output directory! " + outputFilePath.getAbsolutePath());
        }

        FileWriter out = null;

        for (String modName : materialNames.keySet()) {
            File outputFile = new File(outputFilePath, modName + "." + type + ".yml");
            mcMMO.p.getLogger().info("Creating " + outputFile.getName());
            try {
                if (outputFile.exists() && !outputFile.delete()) {
                    mcMMO.p.getLogger().severe("Not able to delete old output file! " + outputFile.getAbsolutePath());
                }

                if (!outputFile.createNewFile()) {
                    mcMMO.p.getLogger().severe("Could not create output file! " + outputFile.getAbsolutePath());
                    continue;
                }

                StringBuilder writer = new StringBuilder();
                HashMap<String, ArrayList<String>> configSections = getConfigSections(type, modName, materialNames);

                if (configSections == null) {
                    mcMMO.p.getLogger().severe("Something went wrong!! type is " + type);
                    return;
                }

                // Write the file, go through each skill and write all the materials
                for (String skillName : configSections.keySet()) {
                    if (skillName.equals("UNIDENTIFIED")) {
                        writer.append("# This isn't a valid config section and all materials in this category need to be").append("\r\n");
                        writer.append("# copy and pasted to a valid section of this config file.").append("\r\n");
                    }
                    writer.append(skillName).append(":").append("\r\n");

                    for (String line : configSections.get(skillName)) {
                        writer.append(line).append("\r\n");
                    }

                    writer.append("\r\n");
                }

                out = new FileWriter(outputFile);
                out.write(writer.toString());
            }
            catch (IOException e) {
                e.printStackTrace();
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                return;
            }
            finally {
                tryClose(out);
            }
        }
    }

    private HashMap<String, ArrayList<String>> getConfigSections(String type, String modName, HashMap<String, ArrayList<String>> materialNames) {
        if (type.equals("blocks")) {
            return getConfigSectionsBlocks(modName, materialNames);
        }
        else if (type.equals("tools")) {
            return getConfigSectionsTools(modName, materialNames);
        }
        else if (type.equals("armor")) {
            return getConfigSectionsArmor(modName, materialNames);
        }

        return null;
    }

    private HashMap<String, ArrayList<String>> getConfigSectionsBlocks(String modName, HashMap<String, ArrayList<String>> materialNames) {
        HashMap<String, ArrayList<String>> configSections = new HashMap<String, ArrayList<String>>();

        // Go through all the materials and categorise them under a skill
        for (String materialName : materialNames.get(modName)) {
            String skillName = "UNIDENTIFIED";
            if (materialName.contains("ORE")) {
                skillName = "Mining";
            }
            else if (materialName.contains("LOG") || materialName.contains("LEAVES")) {
                skillName = "Woodcutting";
            }
            else if (materialName.contains("GRASS") || materialName.contains("FLOWER") || materialName.contains("CROP")) {
                skillName = "Herbalism";
            }
            else if (materialName.contains("DIRT") || materialName.contains("SAND")) {
                skillName = "Excavation";
            }

            if (!configSections.containsKey(skillName)) {
                configSections.put(skillName, new ArrayList<String>());
            }

            ArrayList<String> skillContents = configSections.get(skillName);
            skillContents.add("    " + materialName + "|0:");
            skillContents.add("    " + "    " + "XP_Gain: 99");
            skillContents.add("    " + "    " + "Double_Drops_Enabled: true");

            if (skillName.equals("Mining")) {
                skillContents.add("    " + "    " + "Smelting_XP_Gain: 9");
            }
            else if (skillName.equals("Woodcutting")) {
                skillContents.add("    " + "    " + "Is_Log: " + materialName.contains("LOG"));
            }
        }

        return configSections;
    }

    private HashMap<String, ArrayList<String>> getConfigSectionsTools(String modName, HashMap<String, ArrayList<String>> materialNames) {
        HashMap<String, ArrayList<String>> configSections = new HashMap<String, ArrayList<String>>();

        // Go through all the materials and categorise them under a tool type
        for (String materialName : materialNames.get(modName)) {
            String toolType = "UNIDENTIFIED";
            if (materialName.contains("PICKAXE")) {
                toolType = "Pickaxes";
            }
            else if (materialName.contains("AXE")) {
                toolType = "Axes";
            }
            else if (materialName.contains("BOW")) {
                toolType = "Bows";
            }
            else if (materialName.contains("HOE")) {
                toolType = "Hoes";
            }
            else if (materialName.contains("SHOVEL") || materialName.contains("SPADE")) {
                toolType = "Shovels";
            }
            else if (materialName.contains("SWORD")) {
                toolType = "Swords";
            }

            if (!configSections.containsKey(toolType)) {
                configSections.put(toolType, new ArrayList<String>());
            }

            ArrayList<String> skillContents = configSections.get(toolType);
            skillContents.add("    " + materialName + ":");
            skillContents.add("    " + "    " + "XP_Modifier: 1.0");
            skillContents.add("    " + "    " + "Tier: 1");
            skillContents.add("    " + "    " + "Ability_Enabled: true");
            skillContents.add("    " + "    " + "Repairable: true");
            skillContents.add("    " + "    " + "Repair_Material: REPAIR_MATERIAL_NAME");
            skillContents.add("    " + "    " + "Repair_Material_Data_Value: 0");
            skillContents.add("    " + "    " + "Repair_Material_Quantity: 9");
            skillContents.add("    " + "    " + "Repair_Material_Pretty_Name: Repair Item Name");
            skillContents.add("    " + "    " + "Repair_MinimumLevel: 0");
            skillContents.add("    " + "    " + "Repair_XpMultiplier: 1.0");
            skillContents.add("    " + "    " + "Durability: 9999");
        }

        return configSections;
    }

    private HashMap<String, ArrayList<String>> getConfigSectionsArmor(String modName, HashMap<String, ArrayList<String>> materialNames) {
        HashMap<String, ArrayList<String>> configSections = new HashMap<String, ArrayList<String>>();

        // Go through all the materials and categorise them under an armor type
        for (String materialName : materialNames.get(modName)) {
            String toolType = "UNIDENTIFIED";
            if (materialName.contains("BOOT") || materialName.contains("SHOE")) {
                toolType = "Boots";
            }
            else if (materialName.contains("CHESTPLATE") || materialName.contains("CHEST")) {
                toolType = "Chestplates";
            }
            else if (materialName.contains("HELM") || materialName.contains("HAT")) {
                toolType = "Helmets";
            }
            else if (materialName.contains("LEGGINGS") || materialName.contains("LEGGS") || materialName.contains("PANTS")) {
                toolType = "Leggings";
            }

            if (!configSections.containsKey(toolType)) {
                configSections.put(toolType, new ArrayList<String>());
            }

            ArrayList<String> skillContents = configSections.get(toolType);
            skillContents.add("    " + materialName + ":");
            skillContents.add("    " + "    " + "Repairable: true");
            skillContents.add("    " + "    " + "Repair_Material: REPAIR_MATERIAL_NAME");
            skillContents.add("    " + "    " + "Repair_Material_Data_Value: 0");
            skillContents.add("    " + "    " + "Repair_Material_Quantity: 9");
            skillContents.add("    " + "    " + "Repair_Material_Pretty_Name: Repair Item Name");
            skillContents.add("    " + "    " + "Repair_MinimumLevel: 0");
            skillContents.add("    " + "    " + "Repair_XpMultiplier: 1.0");
            skillContents.add("    " + "    " + "Durability: 9999");
        }

        return configSections;
    }
}
