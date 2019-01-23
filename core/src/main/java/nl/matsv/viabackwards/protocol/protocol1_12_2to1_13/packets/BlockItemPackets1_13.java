/*
 * Copyright (c) 2016 Matsv
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nl.matsv.viabackwards.protocol.protocol1_12_2to1_13.packets;

import com.google.common.base.Optional;
import nl.matsv.viabackwards.ViaBackwards;
import nl.matsv.viabackwards.api.rewriters.BlockItemRewriter;
import nl.matsv.viabackwards.protocol.protocol1_12_2to1_13.Protocol1_12_2To1_13;
import nl.matsv.viabackwards.protocol.protocol1_12_2to1_13.data.BackwardsMappings;
import nl.matsv.viabackwards.protocol.protocol1_12_2to1_13.providers.BackwardsBlockEntityProvider;
import nl.matsv.viabackwards.protocol.protocol1_12_2to1_13.storage.BackwardsBlockStorage;
import us.myles.ViaVersion.api.PacketWrapper;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.minecraft.BlockChangeRecord;
import us.myles.ViaVersion.api.minecraft.Position;
import us.myles.ViaVersion.api.minecraft.chunks.Chunk;
import us.myles.ViaVersion.api.minecraft.chunks.ChunkSection;
import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.api.remapper.PacketHandler;
import us.myles.ViaVersion.api.remapper.PacketRemapper;
import us.myles.ViaVersion.api.type.Type;
import us.myles.ViaVersion.packets.State;
import us.myles.ViaVersion.protocols.protocol1_13to1_12_2.ChatRewriter;
import us.myles.ViaVersion.protocols.protocol1_13to1_12_2.data.BlockIdData;
import us.myles.ViaVersion.protocols.protocol1_13to1_12_2.data.MappingData;
import us.myles.ViaVersion.protocols.protocol1_13to1_12_2.data.SpawnEggRewriter;
import us.myles.ViaVersion.protocols.protocol1_13to1_12_2.types.Chunk1_13Type;
import us.myles.ViaVersion.protocols.protocol1_9_1_2to1_9_3_4.types.Chunk1_9_3_4Type;
import us.myles.ViaVersion.protocols.protocol1_9_3to1_9_1_2.storage.ClientWorld;
import us.myles.viaversion.libs.opennbt.conversion.ConverterRegistry;
import us.myles.viaversion.libs.opennbt.tag.builtin.*;

public class BlockItemPackets1_13 extends BlockItemRewriter<Protocol1_12_2To1_13> {

    private static String NBT_TAG_NAME;

    public static int toOldId(int oldId) {
        if (oldId < 0) {
            oldId = 0; // Some plugins use negative numbers to clear blocks, remap them to air.
        }
        int newId = BackwardsMappings.blockMappings.getNewBlock(oldId);
        if (newId != -1)
            return newId;

        ViaBackwards.getPlatform().getLogger().warning("Missing block completely " + oldId);
        // Default stone
        return 1 << 4;
    }

    @Override
    protected void registerPackets(Protocol1_12_2To1_13 protocol) {
        NBT_TAG_NAME = "ViaVersion|" + protocol.getClass().getSimpleName();
        // Block Action
        protocol.out(State.PLAY, 0x0A, 0x0A, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.POSITION); // Location
                map(Type.UNSIGNED_BYTE); // Action Id
                map(Type.UNSIGNED_BYTE); // Action param
                map(Type.VAR_INT); // Block Id - /!\ NOT BLOCK STATE ID
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int blockId = wrapper.get(Type.VAR_INT, 0);

                        if (blockId == 73)
                            blockId = 25;
                        else if (blockId == 99)
                            blockId = 33;
                        else if (blockId == 92)
                            blockId = 29;
                        else if (blockId == 142)
                            blockId = 54;
                        else if (blockId == 305)
                            blockId = 146;
                        else if (blockId == 249)
                            blockId = 130;
                        else if (blockId == 257)
                            blockId = 138;
                        else if (blockId == 140)
                            blockId = 52;
                        else if (blockId == 472)
                            blockId = 209;
                        else if (blockId >= 483 && blockId <= 498)
                            blockId = blockId - 483 + 219;

                        wrapper.set(Type.VAR_INT, 0, blockId);
                    }
                });
            }
        });

        // Update Block Entity
        protocol.out(State.PLAY, 0x09, 0x09, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.POSITION); // 0 - Position
                map(Type.UNSIGNED_BYTE); // 1 - Action
                map(Type.NBT); // 2 - NBT Data

                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        BackwardsBlockEntityProvider provider = Via.getManager().getProviders().get(BackwardsBlockEntityProvider.class);

                        // TODO conduit handling
                        if (wrapper.get(Type.UNSIGNED_BYTE, 0) == 5) {
                            wrapper.cancel();
                        }

                        wrapper.set(Type.NBT, 0,
                                provider.transform(
                                        wrapper.user(),
                                        wrapper.get(Type.POSITION, 0),
                                        wrapper.get(Type.NBT, 0)
                                ));
                    }
                });
            }
        });

        // Block Change
        protocol.out(State.PLAY, 0x0B, 0x0B, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.POSITION); // 0 - Position

                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int blockState = wrapper.read(Type.VAR_INT);

                        // Store blocks for
                        BackwardsBlockStorage storage = wrapper.user().get(BackwardsBlockStorage.class);
                        storage.checkAndStore(wrapper.get(Type.POSITION, 0), blockState);

                        wrapper.write(Type.VAR_INT, toOldId(blockState));
                    }
                });
            }
        });

        // Multi Block Change
        protocol.out(State.PLAY, 0x0F, 0x10, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.INT); // 0 - Chunk X
                map(Type.INT); // 1 - Chunk Z
                map(Type.BLOCK_CHANGE_RECORD_ARRAY);
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        BackwardsBlockStorage storage = wrapper.user().get(BackwardsBlockStorage.class);

                        for (BlockChangeRecord record : wrapper.get(Type.BLOCK_CHANGE_RECORD_ARRAY, 0)) {
                            int chunkX = wrapper.get(Type.INT, 0);
                            int chunkZ = wrapper.get(Type.INT, 1);
                            int block = record.getBlockId();
                            Position position = new Position(
                                    (long) (record.getHorizontal() >> 4 & 15) + (chunkX * 16),
                                    (long) record.getY(),
                                    (long) (record.getHorizontal() & 15) + (chunkZ * 16));

                            // Store if needed
                            storage.checkAndStore(position, block);

                            // Change to old id
                            record.setBlockId(toOldId(block));
                        }
                    }
                });
            }
        });

        // Windows Items
        protocol.out(State.PLAY, 0x15, 0x14, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.UNSIGNED_BYTE);
                map(Type.FLAT_ITEM_ARRAY, Type.ITEM_ARRAY);
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        Item[] items = wrapper.get(Type.ITEM_ARRAY, 0);
                        for (int i = 0; i < items.length; i++)
                            items[i] = handleItemToClient(items[i]);
                        wrapper.set(Type.ITEM_ARRAY,0,  items);
                    }
                });
            }
        });

        // Set Slot
        protocol.out(State.PLAY, 0x17, 0x16, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.BYTE);
                map(Type.SHORT);
                map(Type.FLAT_ITEM, Type.ITEM);
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        Item item = wrapper.get(Type.ITEM, 0);
                        item = handleItemToClient(item);
                        wrapper.set(Type.ITEM, 0, item);
                    }
                });
            }
        });

        // Chunk packet
        protocol.out(State.PLAY, 0x22, 0x20, new PacketRemapper() {
                    @Override
                    public void registerMap() {
                        handler(new PacketHandler() {
                            @Override
                            public void handle(PacketWrapper wrapper) throws Exception {
                                ClientWorld clientWorld = wrapper.user().get(ClientWorld.class);

                                Chunk1_9_3_4Type type_old = new Chunk1_9_3_4Type(clientWorld);
                                Chunk1_13Type type = new Chunk1_13Type(clientWorld);
                                Chunk chunk = wrapper.read(type);


                                // Handle Block Entities before block rewrite
                                BackwardsBlockEntityProvider provider = Via.getManager().getProviders().get(BackwardsBlockEntityProvider.class);
                                BackwardsBlockStorage storage = wrapper.user().get(BackwardsBlockStorage.class);
                                for (CompoundTag tag : chunk.getBlockEntities()) {
                                    if (!tag.contains("id"))
                                        continue;

                                    String id = (String) tag.get("id").getValue();

                                    // Ignore if we don't handle it
                                    if (!provider.isHandled(id))
                                        continue;

                                    int sectionIndex = ((int) tag.get("y").getValue()) >> 4;
                                    ChunkSection section = chunk.getSections()[sectionIndex];

                                    int x = (int) tag.get("x").getValue();
                                    int y = (int) tag.get("y").getValue();
                                    int z = (int) tag.get("z").getValue();
                                    Position position = new Position((long) x, (long) y, (long) z);

                                    int block = section.getFlatBlock(x & 0xF, y & 0xF, z & 0xF);
                                    storage.checkAndStore(position, block);

                                    provider.transform(wrapper.user(), position, tag);
                                }

                                // Rewrite new blocks to old blocks
                                for (int i = 0; i < chunk.getSections().length; i++) {
                                    ChunkSection section = chunk.getSections()[i];
                                    if (section == null) {
                                        continue;
                                    }

                                    for (int p = 0; p < section.getPaletteSize(); p++) {
                                        int old = section.getPaletteEntry(p);
                                        if (old != 0) {
                                            section.setPaletteEntry(p, toOldId(old));
                                        }
                                    }
                                }

                                // Rewrite biome id 255 to plains
                                if (chunk.isBiomeData()) {
                                    for (int i = 0; i < 256; i++) {
                                        chunk.getBiomeData()[i] = 1; // Plains
                                    }
                                }

                                wrapper.write(type_old, chunk);
                            }
                        });
                    }
                }
        );

        // Effect
        protocol.out(State.PLAY, 0x23, 0x21, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.INT); // Effect Id
                map(Type.POSITION); // Location
                map(Type.INT); // Data
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int id = wrapper.get(Type.INT, 0);
                        int data = wrapper.get(Type.INT, 1);
                        if (id == 1010) { // Play record
                            wrapper.set(Type.INT, 1, data = MappingData.oldToNewItems.inverse().get(data) >> 4);
                        } else if (id == 2001) { // Block break + block break sound
                            data = toOldId(data);
                            int blockId = data >> 4;
                            int blockData = data & 0xF;
                            wrapper.set(Type.INT, 1, data = (blockId & 0xFFF) | (blockData << 12));
                        }
                    }
                });
            }
        });

        // Map
        protocol.out(State.PLAY, 0x26, 0x24, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT);
                map(Type.BYTE);
                map(Type.BOOLEAN);
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        int iconCount = wrapper.passthrough(Type.VAR_INT);
                        for (int i = 0; i < iconCount; i++) {
                            int type = wrapper.read(Type.VAR_INT);
                            byte x = wrapper.read(Type.BYTE);
                            byte z = wrapper.read(Type.BYTE);
                            byte direction = wrapper.read(Type.BYTE);
                            if (wrapper.read(Type.BOOLEAN)) {
                                wrapper.read(Type.STRING);
                            }
                            if (type > 9) continue;
                            wrapper.write(Type.BYTE, (byte) ((type << 4) | (direction & 0x0F)));
                            wrapper.write(Type.BYTE, x);
                            wrapper.write(Type.BYTE, z);
                        }
                    }
                });
            }
        });

        // Entity Equipment
        protocol.out(State.PLAY, 0x42, 0x3F, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT);
                map(Type.VAR_INT);
                map(Type.FLAT_ITEM, Type.ITEM);
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        Item item = wrapper.get(Type.ITEM, 0);
                        item = handleItemToClient(item);
                        wrapper.set(Type.ITEM, 0, item);
                    }
                });
            }
        });


        // Set Creative Slot
        protocol.in(State.PLAY, 0x24, 0x1B, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.SHORT);
                map(Type.ITEM, Type.FLAT_ITEM);
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        Item item = wrapper.get(Type.FLAT_ITEM, 0);
                        item = handleItemToServer(item);
                        wrapper.set(Type.FLAT_ITEM, 0, item);
                    }
                });
            }
        });

        // Click Window
        protocol.in(State.PLAY, 0x08, 0x07, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.UNSIGNED_BYTE);
                map(Type.SHORT);
                map(Type.BYTE);
                map(Type.SHORT);
                map(Type.VAR_INT);
                map(Type.ITEM, Type.FLAT_ITEM);
                handler(new PacketHandler() {
                    @Override
                    public void handle(PacketWrapper wrapper) throws Exception {
                        Item item = wrapper.get(Type.FLAT_ITEM, 0);
                        item = handleItemToServer(item);
                        wrapper.set(Type.FLAT_ITEM, 0, item);
                    }
                });
            }
        });
    }

    @Override
    protected void registerRewrites() {
        rewrite(245).repItem(new Item((short) 241, (byte) 1, (short) -1, getNamedTag("1.12 Acacia Button")));
        rewrite(243).repItem(new Item((short) 241, (byte) 1, (short) -1, getNamedTag("1.12 Birch Button")));
        rewrite(242).repItem(new Item((short) 241, (byte) 1, (short) -1, getNamedTag("1.12 Spruce Button")));
        rewrite(244).repItem(new Item((short) 241, (byte) 1, (short) -1, getNamedTag("1.12 Jungle Button")));
        rewrite(246).repItem(new Item((short) 241, (byte) 1, (short) -1, getNamedTag("1.12 Dark Oak Button")));

        rewrite(191).repItem(new Item((short) 160, (byte) 1, (short) -1, getNamedTag("1.12 Acacia Trapdoor")));
        rewrite(189).repItem(new Item((short) 160, (byte) 1, (short) -1, getNamedTag("1.12 Birch Trapdoor")));
        rewrite(188).repItem(new Item((short) 160, (byte) 1, (short) -1, getNamedTag("1.12 Spruce Trapdoor")));
        rewrite(190).repItem(new Item((short) 160, (byte) 1, (short) -1, getNamedTag("1.12 Jungle Trapdoor")));
        rewrite(192).repItem(new Item((short) 160, (byte) 1, (short) -1, getNamedTag("1.12 Dark Oak Trapdoor")));

        rewrite(164).repItem(new Item((short) 187, (byte) 1, (short) -1, getNamedTag("1.12 Acacia Pressure Plate")));
        rewrite(162).repItem(new Item((short) 187, (byte) 1, (short) -1, getNamedTag("1.12 Birch Pressure Plate")));
        rewrite(161).repItem(new Item((short) 187, (byte) 1, (short) -1, getNamedTag("1.12 Spruce Pressure Plate")));
        rewrite(163).repItem(new Item((short) 187, (byte) 1, (short) -1, getNamedTag("1.12 Jungle Pressure Plate")));
        rewrite(165).repItem(new Item((short) 187, (byte) 1, (short) -1, getNamedTag("1.12 Dark Oak Pressure Plate")));

        rewrite(762).repItem(new Item((short) 544, (byte) 1, (short) -1, getNamedTag("1.12 Acacia Boat")));
        rewrite(760).repItem(new Item((short) 544, (byte) 1, (short) -1, getNamedTag("1.12 Birch Boat")));
        rewrite(759).repItem(new Item((short) 544, (byte) 1, (short) -1, getNamedTag("1.12 Spruce Boat")));
        rewrite(761).repItem(new Item((short) 544, (byte) 1, (short) -1, getNamedTag("1.12 Jungle Boat")));
        rewrite(763).repItem(new Item((short) 544, (byte) 1, (short) -1, getNamedTag("1.12 Dark Oak Boat")));

        rewrite(453).repItem(new Item((short) 300, (byte) 1, (short) -1, getNamedTag("1.12 Blue Ice")));
        rewrite(611).repItem(new Item((short) 1, (byte) 1, (short) -1, getNamedTag("1.12 Dried Kelp")));//TODO

        rewrite(547).repItem(new Item((short) 538, (byte) 1, (short) -1, getNamedTag("1.12 Bucket of Pufferfish")));
        rewrite(548).repItem(new Item((short) 538, (byte) 1, (short) -1, getNamedTag("1.12 Bucket of Salmon")));
        rewrite(549).repItem(new Item((short) 538, (byte) 1, (short) -1, getNamedTag("1.12 Bucket of Cod")));
        rewrite(650).repItem(new Item((short) 538, (byte) 1, (short) -1, getNamedTag("1.12 Bucket of Tropical Fish")));

        rewrite(784).repItem(new Item((short) 543, (byte) 1, (short) -1, getNamedTag("1.12 Heart of the Sea")));
        rewrite(783).repItem(new Item((short) 587, (byte) 1, (short) -1, getNamedTag("1.12 Nautilus Shell")));
        rewrite(782).repItem(new Item((short) 282, (byte) 1, (short) -1, getNamedTag("1.12 Phantom Membrane")));
        rewrite(465).repItem(new Item((short) 510, (byte) 1, (short) -1, getNamedTag("1.12 Turtle Shell")));

        rewrite(554).repItem(new Item((short) 76, (byte) 1, (short) -1, getNamedTag("1.12 Kelp")));

        //Spawn Eggs:
        rewrite(643).repItem(new Item((short) 662, (byte) 1, (short) -1, getNamedTag("1.12 Drowned Spawn Egg")));
        rewrite(658).repItem(new Item((short) 662, (byte) 1, (short) -1, getNamedTag("1.12 Phantom Spawn Egg")));
        rewrite(641).repItem(new Item((short) 662, (byte) 1, (short) -1, getNamedTag("1.12 Dolphin Spawn Egg")));
        rewrite(674).repItem(new Item((short) 662, (byte) 1, (short) -1, getNamedTag("1.12 Turtle Spawn Egg")));
        rewrite(638).repItem(new Item((short) 662, (byte) 1, (short) -1, getNamedTag("1.12 Cod Spawn Egg")));
        rewrite(663).repItem(new Item((short) 662, (byte) 1, (short) -1, getNamedTag("1.12 Salmon Spawn Egg")));
        rewrite(661).repItem(new Item((short) 662, (byte) 1, (short) -1, getNamedTag("1.12 Pufferfish Spawn Egg")));
        rewrite(673).repItem(new Item((short) 662, (byte) 1, (short) -1, getNamedTag("1.12 Tropical Fish Spawn Egg")));

        //Corals
        rewrite(438).repItem(new Item((short) 100, (byte) 1, (short) -1, getNamedTag("1.12 Tube Coral")));
        rewrite(439).repItem(new Item((short) 106, (byte) 1, (short) -1, getNamedTag("1.12 Brain Coral")));
        rewrite(440).repItem(new Item((short) 101, (byte) 1, (short) -1, getNamedTag("1.12 Bubble Coral")));
        rewrite(441).repItem(new Item((short) 103, (byte) 1, (short) -1, getNamedTag("1.12 Fire Coral")));
        rewrite(442).repItem(new Item((short) 98, (byte) 1, (short) -1, getNamedTag("1.12 Horn Coral")));

        rewrite(438).repItem(new Item((short) 78, (byte) 1, (short) -1, getNamedTag("1.12 Tube Coral")));
        rewrite(439).repItem(new Item((short) 78, (byte) 1, (short) -1, getNamedTag("1.12 Brain Coral")));
        rewrite(440).repItem(new Item((short) 78, (byte) 1, (short) -1, getNamedTag("1.12 Bubble Coral")));
        rewrite(441).repItem(new Item((short) 78, (byte) 1, (short) -1, getNamedTag("1.12 Fire Coral")));
        rewrite(442).repItem(new Item((short) 78, (byte) 1, (short) -1, getNamedTag("1.12 Horn Coral")));

        rewrite(427).repItem(new Item((short) 561, (byte) 1, (short) -1, getNamedTag("1.12 Turtle Egg")));
        rewrite(466).repItem(new Item((short) 582, (byte) 1, (short) -1, getNamedTag("1.12 Scute")));

        rewrite(781).repItem(new Item((short) 488, (byte) 1, (short) -1, getNamedTag("1.12 Trident")));
    }

    @Override
    protected CompoundTag getNamedTag(String text) {
        CompoundTag tag = new CompoundTag("");
        tag.put(new CompoundTag("display"));
        ((CompoundTag) tag.get("display")).put(new StringTag("Name", ChatRewriter.legacyTextToJson(text)));
        return tag;
    }

    @Override
    protected Item handleItemToClient(Item item) {
        if (item == null) return null;
        System.out.println("Input ID: " + item.getId());
        item = super.handleItemToClient(item);
        System.out.println("Backwards ID:  " + item.getId());

        Integer rawId = null;
        boolean gotRawIdFromTag = false;

        CompoundTag tag = item.getTag();

        // Use tag to get original ID and data
        if (tag != null) {
            // Check for valid tag
            if (tag.get(NBT_TAG_NAME) instanceof IntTag) {
                rawId = (Integer) tag.get(NBT_TAG_NAME).getValue();
                // Remove the tag
                tag.remove(NBT_TAG_NAME);
                gotRawIdFromTag = true;
            }
        }

        if (rawId == null) {
            Integer oldId = MappingData.oldToNewItems.inverse().get((int) item.getId());
            if (oldId != null) {
                // Handle spawn eggs
                Optional<String> eggEntityId = SpawnEggRewriter.getEntityId(oldId);
                if (eggEntityId.isPresent()) {
                    rawId = 383 << 16;
                    if (tag == null)
                        item.setTag(tag = new CompoundTag("tag"));
                    if (!tag.contains("EntityTag")) {
                        CompoundTag entityTag = new CompoundTag("EntityTag");
                        entityTag.put(new StringTag("id", eggEntityId.get()));
                        tag.put(entityTag);
                    }
                } else {
                    rawId = (oldId >> 4) << 16 | oldId & 0xF;
                }
            }
        }

        if (rawId == null) {
            if (!Via.getConfig().isSuppress1_13ConversionErrors() || Via.getManager().isDebug()) {
                Via.getPlatform().getLogger().warning("Failed to get 1.12 item for " + item.getId());
            }
            rawId = 0x10000; // Stone
        }

        item.setId((short) (rawId >> 16));
        item.setData((short) (rawId & 0xFFFF));

        // NBT changes
        if (tag != null) {
            if (isDamageable(item.getId())) {
                if (tag.get("Damage") instanceof IntTag) {
                    if (!gotRawIdFromTag)
                        item.setData((short) (int) tag.get("Damage").getValue());
                    tag.remove("Damage");
                }
            }

            if (item.getId() == 358) { // map
                if (tag.get("map") instanceof IntTag) {
                    if (!gotRawIdFromTag)
                        item.setData((short) (int) tag.get("map").getValue());
                    tag.remove("map");
                }
            }

            if (item.getId() == 442 || item.getId() == 425) { // shield / banner
                if (tag.get("BlockEntityTag") instanceof CompoundTag) {
                    CompoundTag blockEntityTag = tag.get("BlockEntityTag");
                    if (blockEntityTag.get("Base") instanceof IntTag) {
                        IntTag base = blockEntityTag.get("Base");
                        base.setValue(15 - base.getValue()); // invert color id
                    }
                    if (blockEntityTag.get("Patterns") instanceof ListTag) {
                        for (Tag pattern : (ListTag) blockEntityTag.get("Patterns")) {
                            if (pattern instanceof CompoundTag) {
                                IntTag c = ((CompoundTag) pattern).get("Color");
                                c.setValue(15 - c.getValue()); // Invert color id
                            }
                        }
                    }
                }
            }
            // Display Name now uses JSON
            if (tag.get("display") instanceof CompoundTag) {
                CompoundTag display = tag.get("display");
                if (((CompoundTag) tag.get("display")).get("Name") instanceof StringTag) {
                    StringTag name = display.get("Name");
                    StringTag via = display.get(NBT_TAG_NAME + "|Name");
                    name.setValue(
                            via != null ? via.getValue() : ChatRewriter.jsonTextToLegacy(
                                    name.getValue()
                            )
                    );
                    display.remove(NBT_TAG_NAME + "|Name");
                }
            }

            // ench is now Enchantments and now uses identifiers
            if (tag.get("Enchantments") instanceof ListTag) {
                ListTag enchantments = tag.get("Enchantments");
                ListTag ench = new ListTag("ench", CompoundTag.class);
                for (Tag enchantmentEntry : enchantments) {
                    if (enchantmentEntry instanceof CompoundTag) {
                        CompoundTag enchEntry = new CompoundTag("");
                        String newId = (String) ((CompoundTag) enchantmentEntry).get("id").getValue();
                        Short oldId = MappingData.oldEnchantmentsIds.inverse().get(newId);
                        if (oldId == null && newId.startsWith("viaversion:legacy/")) {
                            oldId = Short.valueOf(newId.substring(18));
                        }
                        enchEntry.put(
                                new ShortTag(
                                        "id",
                                        oldId
                                )
                        );
                        enchEntry.put(new ShortTag("lvl", (Short) ((CompoundTag) enchantmentEntry).get("lvl").getValue()));
                        ench.add(enchEntry);
                    }
                }
                tag.remove("Enchantment");
                tag.put(ench);
            }
            if (tag.get("StoredEnchantments") instanceof ListTag) {
                ListTag storedEnch = tag.get("StoredEnchantments");
                ListTag newStoredEnch = new ListTag("StoredEnchantments", CompoundTag.class);
                for (Tag enchantmentEntry : storedEnch) {
                    if (enchantmentEntry instanceof CompoundTag) {
                        CompoundTag enchEntry = new CompoundTag("");
                        String newId = (String) ((CompoundTag) enchantmentEntry).get("id").getValue();
                        Short oldId = MappingData.oldEnchantmentsIds.inverse().get(newId);
                        if (oldId == null && newId.startsWith("viaversion:legacy/")) {
                            oldId = Short.valueOf(newId.substring(18));
                        }
                        enchEntry.put(
                                new ShortTag(
                                        "id",
                                        oldId
                                )
                        );
                        enchEntry.put(new ShortTag("lvl", (Short) ((CompoundTag) enchantmentEntry).get("lvl").getValue()));
                        newStoredEnch.add(enchEntry);
                    }
                }
                tag.remove("StoredEnchantments");
                tag.put(newStoredEnch);
            }
            if (tag.get(NBT_TAG_NAME + "|CanPlaceOn") instanceof ListTag) {
                tag.put(ConverterRegistry.convertToTag(
                        "CanPlaceOn",
                        ConverterRegistry.convertToValue(tag.get(NBT_TAG_NAME + "|CanPlaceOn"))
                ));
                tag.remove(NBT_TAG_NAME + "|CanPlaceOn");
            } else if (tag.get("CanPlaceOn") instanceof ListTag) {
                ListTag old = tag.get("CanPlaceOn");
                ListTag newCanPlaceOn = new ListTag("CanPlaceOn", StringTag.class);
                for (Tag oldTag : old) {
                    Object value = oldTag.getValue();
                    String[] newValues = BlockIdData.fallbackReverseMapping.get(value instanceof String
                            ? ((String) value).replace("minecraft:", "")
                            : null);
                    if (newValues != null) {
                        for (String newValue : newValues) {
                            newCanPlaceOn.add(new StringTag("", newValue));
                        }
                    } else {
                        newCanPlaceOn.add(oldTag);
                    }
                }
                tag.put(newCanPlaceOn);
            }
            if (tag.get(NBT_TAG_NAME + "|CanDestroy") instanceof ListTag) {
                tag.put(ConverterRegistry.convertToTag(
                        "CanDestroy",
                        ConverterRegistry.convertToValue(tag.get(NBT_TAG_NAME + "|CanDestroy"))
                ));
                tag.remove(NBT_TAG_NAME + "|CanDestroy");
            } else if (tag.get("CanDestroy") instanceof ListTag) {
                ListTag old = tag.get("CanDestroy");
                ListTag newCanDestroy = new ListTag("CanDestroy", StringTag.class);
                for (Tag oldTag : old) {
                    Object value = oldTag.getValue();
                    String[] newValues = BlockIdData.fallbackReverseMapping.get(value instanceof String
                            ? ((String) value).replace("minecraft:", "")
                            : null);
                    if (newValues != null) {
                        for (String newValue : newValues) {
                            newCanDestroy.add(new StringTag("", newValue));
                        }
                    } else {
                        newCanDestroy.add(oldTag);
                    }
                }
                tag.put(newCanDestroy);
            }
        }
        System.out.println("ViaVer Id: " + item.getId());
        return item;
    }

    @Override
    protected Item handleItemToServer(Item item) {
        item = super.handleItemToServer(item);
        if (item == null) return null;
        CompoundTag tag = item.getTag();

        // Save original id
        int originalId = (item.getId() << 16 | item.getData() & 0xFFFF);

        int rawId = (item.getId() << 4 | item.getData() & 0xF);

        // NBT Additions
        if (isDamageable(item.getId())) {
            if (tag == null) item.setTag(tag = new CompoundTag("tag"));
            tag.put(new IntTag("Damage", item.getData()));
        }
        if (item.getId() == 358) { // map
            if (tag == null) item.setTag(tag = new CompoundTag("tag"));
            tag.put(new IntTag("map", item.getData()));
        }

        // NBT Changes
        if (tag != null) {
            // Invert shield color id
            if (item.getId() == 442 || item.getId() == 425) {
                if (tag.get("BlockEntityTag") instanceof CompoundTag) {
                    CompoundTag blockEntityTag = tag.get("BlockEntityTag");
                    if (blockEntityTag.get("Base") instanceof IntTag) {
                        IntTag base = blockEntityTag.get("Base");
                        base.setValue(15 - base.getValue());
                    }
                    if (blockEntityTag.get("Patterns") instanceof ListTag) {
                        for (Tag pattern : (ListTag) blockEntityTag.get("Patterns")) {
                            if (pattern instanceof CompoundTag) {
                                IntTag c = ((CompoundTag) pattern).get("Color");
                                c.setValue(15 - c.getValue()); // Invert color id
                            }
                        }
                    }
                }
            }
            // Display Name now uses JSON
            if (tag.get("display") instanceof CompoundTag) {
                CompoundTag display = tag.get("display");
                if (display.get("Name") instanceof StringTag) {
                    StringTag name = display.get("Name");
                    display.put(new StringTag(NBT_TAG_NAME + "|Name", name.getValue()));
                    name.setValue(
                            ChatRewriter.legacyTextToJson(
                                    name.getValue()
                            )
                    );
                }
            }
            // ench is now Enchantments and now uses identifiers
            if (tag.get("ench") instanceof ListTag) {
                ListTag ench = tag.get("ench");
                ListTag enchantments = new ListTag("Enchantments", CompoundTag.class);
                for (Tag enchEntry : ench) {
                    if (enchEntry instanceof CompoundTag) {
                        CompoundTag enchantmentEntry = new CompoundTag("");
                        short oldId = ((Number) ((CompoundTag) enchEntry).get("id").getValue()).shortValue();
                        String newId = MappingData.oldEnchantmentsIds.get(oldId);
                        if (newId == null) {
                            newId = "viaversion:legacy/" + oldId;
                        }
                        enchantmentEntry.put(new StringTag("id", newId));
                        enchantmentEntry.put(new ShortTag("lvl", ((Number) ((CompoundTag) enchEntry).get("lvl").getValue()).shortValue()));
                        enchantments.add(enchantmentEntry);
                    }
                }
                tag.remove("ench");
                tag.put(enchantments);
            }
            if (tag.get("StoredEnchantments") instanceof ListTag) {
                ListTag storedEnch = tag.get("StoredEnchantments");
                ListTag newStoredEnch = new ListTag("StoredEnchantments", CompoundTag.class);
                for (Tag enchEntry : storedEnch) {
                    if (enchEntry instanceof CompoundTag) {
                        CompoundTag enchantmentEntry = new CompoundTag("");
                        short oldId = ((Number) ((CompoundTag) enchEntry).get("id").getValue()).shortValue();
                        String newId = MappingData.oldEnchantmentsIds.get(oldId);
                        if (newId == null) {
                            newId = "viaversion:legacy/" + oldId;
                        }
                        enchantmentEntry.put(new StringTag("id",
                                newId
                        ));
                        enchantmentEntry.put(new ShortTag("lvl", ((Number) ((CompoundTag) enchEntry).get("lvl").getValue()).shortValue()));
                        newStoredEnch.add(enchantmentEntry);
                    }
                }
                tag.remove("StoredEnchantments");
                tag.put(newStoredEnch);
            }
            if (tag.get("CanPlaceOn") instanceof ListTag) {
                ListTag old = tag.get("CanPlaceOn");
                ListTag newCanPlaceOn = new ListTag("CanPlaceOn", StringTag.class);
                tag.put(ConverterRegistry.convertToTag(NBT_TAG_NAME + "|CanPlaceOn", ConverterRegistry.convertToValue(old))); // There will be data losing
                for (Tag oldTag : old) {
                    Object value = oldTag.getValue();
                    String[] newValues = BlockIdData.blockIdMapping.get(value instanceof String
                            ? ((String) value).replace("minecraft:", "")
                            : null);
                    if (newValues != null) {
                        for (String newValue : newValues) {
                            newCanPlaceOn.add(new StringTag("", newValue));
                        }
                    } else {
                        newCanPlaceOn.add(oldTag);
                    }
                }
                tag.put(newCanPlaceOn);
            }
            if (tag.get("CanDestroy") instanceof ListTag) {
                ListTag old = tag.get("CanDestroy");
                ListTag newCanDestroy = new ListTag("CanDestroy", StringTag.class);
                tag.put(ConverterRegistry.convertToTag(NBT_TAG_NAME + "|CanDestroy", ConverterRegistry.convertToValue(old))); // There will be data losing
                for (Tag oldTag : old) {
                    Object value = oldTag.getValue();
                    String[] newValues = BlockIdData.blockIdMapping.get(value instanceof String
                            ? ((String) value).replace("minecraft:", "")
                            : null);
                    if (newValues != null) {
                        for (String newValue : newValues) {
                            newCanDestroy.add(new StringTag("", newValue));
                        }
                    } else {
                        newCanDestroy.add(oldTag);
                    }
                }
                tag.put(newCanDestroy);
            }
            // Handle SpawnEggs
            if (item.getId() == 383) {
                if (tag.get("EntityTag") instanceof CompoundTag) {
                    CompoundTag entityTag = tag.get("EntityTag");
                    if (entityTag.get("id") instanceof StringTag) {
                        StringTag identifier = entityTag.get("id");
                        rawId = SpawnEggRewriter.getSpawnEggId(identifier.getValue());
                        if (rawId == -1) {
                            rawId = 25100288; // Bat fallback
                        } else {
                            entityTag.remove("id");
                            if (entityTag.isEmpty())
                                tag.remove("EntityTag");
                        }
                    } else {
                        // Fallback to bat
                        rawId = 25100288;
                    }
                } else {
                    // Fallback to bat
                    rawId = 25100288;
                }
            }
            if (tag.isEmpty()) {
                item.setTag(tag = null);
            }
        }

        if (!MappingData.oldToNewItems.containsKey(rawId)) {
            if (!isDamageable(item.getId()) && item.getId() != 358) { // Map
                if (tag == null) item.setTag(tag = new CompoundTag("tag"));
                tag.put(new IntTag(NBT_TAG_NAME, originalId)); // Data will be lost, saving original id
            }
            if (item.getId() == 31 && item.getData() == 0) { // Shrub was removed
                rawId = 32 << 4; // Dead Bush
            } else if (MappingData.oldToNewItems.containsKey(rawId & ~0xF)) {
                rawId &= ~0xF; // Remove data
            } else {
                if (!Via.getConfig().isSuppress1_13ConversionErrors() || Via.getManager().isDebug()) {
                    Via.getPlatform().getLogger().warning("Failed to get 1.13 item for " + item.getId());
                }
                rawId = 16; // Stone
            }
        }

        item.setId(MappingData.oldToNewItems.get(rawId).shortValue());
        item.setData((short) 0);
        return item;
    }

    public static boolean isDamageable(int id) {
        return id >= 256 && id <= 259 // iron shovel, pickaxe, axe, flint and steel
                || id == 261 // bow
                || id >= 267 && id <= 279 // iron sword, wooden+stone+diamond swords, shovels, pickaxes, axes
                || id >= 283 && id <= 286 // gold sword, shovel, pickaxe, axe
                || id >= 290 && id <= 294 // hoes
                || id >= 298 && id <= 317 // armors
                || id == 346 // fishing rod
                || id == 359 // shears
                || id == 398 // carrot on a stick
                || id == 442 // shield
                || id == 443; // elytra
    }
}
