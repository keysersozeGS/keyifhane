package org.birlik.room;

import org.birlik.model.GameType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomCounter = new AtomicInteger(1000);

    public Room createRoom(String roomName, String hostId, GameType gameType) {
        String roomId = "ROOM-" + (100000 + new Random().nextInt(899999));
        if (roomName == null || roomName.trim().isEmpty()) {
            roomName = "Keyifhane Masa #" + (roomCounter.getAndIncrement() % 100);
        }
        Room room = new Room(roomId, roomName, hostId, gameType);
        rooms.put(roomId, room);
        return room;
    }

    public Room getRoom(String roomId) {
        if (roomId == null) return null;
        return rooms.get(roomId.toUpperCase());
    }

    public Collection<Room> getAllRooms() {
        return rooms.values();
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }

    public Room findRoomBySession(String sessionId) {
        for (Room r : rooms.values()) {
            for (String sId : r.getSeatSessionIds()) {
                if (sessionId.equals(sId)) {
                    return r;
                }
            }
        }
        return null;
    }
}
