package org.birlik.controller;

import java.util.HashMap;
import java.util.Map;

import org.birlik.room.RoomManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LobbyController {

    private final RoomManager roomManager;

    public LobbyController(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> map = new HashMap<>();
        map.put("appName", "Keyifhane Online Oyun Platformu");
        map.put("version", "1.0.0");
        map.put("activeRooms", roomManager.getAllRooms().size());
        return map;
    }
}
