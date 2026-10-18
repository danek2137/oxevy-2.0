package me.alpha432.oxevy.event.impl.input;

import me.alpha432.oxevy.event.Event;

public class MouseMoveEvent extends Event {
    private final double dx;
    private final double dy;
    private final long timeNs;

    public MouseMoveEvent(double dx, double dy, long timeNs) {
        this.dx = dx;
        this.dy = dy;
        this.timeNs = timeNs;
    }

    public double getDx() {
        return dx;
    }

    public double getDy() {
        return dy;
    }

    public long getTimeNs() {
        return timeNs;
    }
}