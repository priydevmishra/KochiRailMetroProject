package com.example.KochiRailMetroProject.KochiRailMetro.Entity;


public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    EMAIL_SYNC,   // 🔹 new action for Gmail imports
    LOGIN,
    LOGOUT
}

