package com.cowork.dataaccess.entity;

import java.time.DayOfWeek;

public enum DayType {
    WEEKDAY,
    WEEKEND;

    public static DayType of(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? WEEKEND : WEEKDAY;
    }
}
