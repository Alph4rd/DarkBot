package com.github.manolo8.darkbot.core.api;

import com.github.manolo8.darkbot.utils.login.LoginData;
import eu.darkbot.api.DarkBoat;

public class DarkBoatAdapter extends ApiAdapter {
    private final LoginData loginData;
    private final DarkBoat API = new DarkBoat();

    public DarkBoatAdapter(LoginData loginData) {
        this.loginData = loginData;
    }

    @Override
    public void createWindow() {
        String url = "https://" + loginData.getUrl() + "/",
                sid = "dosid=" + loginData.getSid();

        API.setData(url, sid, loginData.getPreloaderUrl(), loginData.getParams());
        Thread apiThread = new Thread(API::createWindow);
        apiThread.setDaemon(true);
        apiThread.start();
    }

    @Override
    public void setVisible(boolean visible) {
        API.setVisible(visible);
    }

    @Override
    public boolean isValid() {
        return API.isValid();
    }

    @Override
    public void mouseMove(int x, int y) {
        API.mouseMove(x, y);
    }

    @Override
    public void mouseClick(int x, int y) {
        API.mouseClick(x, y);
    }

    @Override
    public void keyboardClick(char btn) {
        API.keyClick(Character.toUpperCase(btn));
    }

    @Override
    public double readMemoryDouble(long address) {
        return API.readDouble(address);
    }

    @Override
    public long readMemoryLong(long address) {
        return API.readLong(address);
    }

    @Override
    public int readMemoryInt(long address) {
        return API.readInt(address);
    }

    @Override
    public boolean readMemoryBoolean(long address) {
        return API.readBoolean(address);
    }

    @Override
    public byte[] readMemory(long address, int length) {
        return API.readBytes(address, length);
    }

    @Override
    public void writeMemoryDouble(long address, double value) {
        API.writeDouble(address, value);
    }

    @Override
    public void writeMemoryLong(long address, long value) {
        API.writeLong(address, value);
    }

    @Override
    public void writeMemoryInt(long address, int value) {
        API.writeInt(address, value);
    }

    @Override
    public long[] queryMemoryInt(int value, int maxQuantity) {
        return API.queryInt(value, maxQuantity);
    }

    @Override
    public long[] queryMemoryLong(long value, int maxQuantity) {
        return API.queryLong(value, maxQuantity);
    }

    @Override
    public long[] queryMemory(byte[] query, int maxQuantity) {
        return API.queryBytes(query, maxQuantity);
    }

    @Override
    public void handleRefresh() {
        API.reload();
    }
}