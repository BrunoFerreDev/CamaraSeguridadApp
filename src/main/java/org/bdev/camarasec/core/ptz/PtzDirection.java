package org.bdev.camarasec.core.ptz;

public enum PtzDirection {
    UP(0, 60),
    DOWN(0, -60),
    LEFT(-60, 0),
    RIGHT(60, 0),
    STOP(0, 0);

    private final int pan;
    private final int tilt;

    PtzDirection(int pan, int tilt) {
        this.pan = pan;
        this.tilt = tilt;
    }

    public int getPan() {
        return pan;
    }

    public int getTilt() {
        return tilt;
    }
}
