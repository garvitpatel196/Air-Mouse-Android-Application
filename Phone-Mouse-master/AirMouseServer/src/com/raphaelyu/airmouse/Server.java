package com.raphaelyu.airmouse;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class Server {

    final static boolean DEBUG_MODE = false;

    
    final static int PROTOCAL_VERSION = 2;
    final static short SERVER_PORT = 5329;
    final static int MAX_PACKET_LENGTH = 64;
    final static byte PACKET_TYPE_DISCOVER = 0x1;
    final static byte PACKET_TYPE_REPLY = 0x2;
    final static byte PACKET_TYPE_MOVE = 0x10;
    final static byte PACKET_TYPE_PRESS = 0x11;
    final static byte PACKET_TYPE_RELEASE = 0x12;
    final static byte PACKET_MOUSE_BUTTON_LEFT = 1;
    final static byte PACKET_MOUSE_BUTTON_RIGHT = 2;
    final static byte PACKET_MOUSE_BUTTON_MIDDLE = 3;

    private static final PrintWriter STD_OUT = new PrintWriter(System.out, true);

    private final EventQueue mEvents = EventQueue.getInstance();
    private Robot mRobot;
    private int mMaxMotionDist;

    
    private class PacketThread extends Thread {
        private final DatagramChannel mServerChannel;
        private final ByteBuffer buffer;

        public PacketThread() throws IOException {
            super("Receiver");
            mServerChannel = DatagramChannel.open();
            mServerChannel.socket().bind(new InetSocketAddress(SERVER_PORT));
            buffer = ByteBuffer.allocate(MAX_PACKET_LENGTH);
        }

        @Override
        public void run() {
            while (true) {
                buffer.clear();
                SocketAddress addr;
                try {
                    addr = mServerChannel.receive(buffer);
                    buffer.flip();
                    if (buffer.limit() > 0) {
                        byte type = buffer.get();
                       System.out.println(type+" hello");
                        printLog("re ceived from: " + addr.toString());
                        switch (type) {
                        case PACKET_TYPE_DISCOVER:
                            printlnLog(", type: DISCOVER");
                            if (buffer.hasRemaining()) {
                                byte ver = buffer.get();
                                System.out.println(ver);
                                buffer.clear();
                                if (PROTOCAL_VERSION >= ver) {
                                    buffer.put(PACKET_TYPE_REPLY);
                                    buffer.flip();
                                    try {
                                        mServerChannel.send(buffer, addr);
                                    } catch (IOException e) {
                                    }
                                }
                            }
                            break;
                        case PACKET_TYPE_MOVE: {
                            printlnLog(", type: MOVE");
                            long timestamp = buffer.getLong();
                            float moveX = buffer.getFloat();
                            float moveY = buffer.getFloat();
                            MouseEvent event = MouseEvent.createMoveEvent(timestamp, moveX, moveY);
                            mEvents.offer(event);
                            break;
                        }
                        case PACKET_TYPE_PRESS: {
                            printlnLog(", type: PRESS");
                            long timestamp = buffer.getLong();
                            int button = convertButtonMask(buffer.getInt());
                            if (button != -1) {
                                MouseEvent event = MouseEvent.createPressEvent(timestamp, button);
                                mEvents.offer(event);
                            }
                            break;
                        }
                        case PACKET_TYPE_RELEASE: {
                            printlnLog(", type: RELEASE");
                            long timestamp = buffer.getLong();
                            int button = convertButtonMask(buffer.getInt());
                            if (button != -1) {
                                MouseEvent event = MouseEvent.createReleaseEvent(timestamp, button);
                                mEvents.offer(event);
                            }
                            break;
                        }
                        default:
                            // otherwise ignore the packet.
                            printlnLog(", type: UNKNOWN, " + type);
                        }
                    }
                } catch (IOException e) {
                }
            }   // end of while(true)
        }
    }

    private void init() throws AWTException {
        mRobot = new Robot();
        Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screenHeight = scrSize.height;
        int screenWidth = scrSize.width;
        mMaxMotionDist = (screenHeight < screenWidth) ? screenHeight : screenWidth;
        mMaxMotionDist /= 7.0f;
    }

    public void start() throws InterruptedException, AWTException {
        STD_OUT.println("Initializing...");
        init();

        STD_OUT.println("Starting packet dispatcher...");
        PacketThread th;
        try {
            th = new PacketThread();
            th.start();

            STD_OUT.println("The Air Mouse Server has started!");
            while (true) {
                long lastTime = 0l;
                MouseEvent event = mEvents.take();
                if (event.timestamp > lastTime) {
                    switch (event.type) {
                    case MouseEvent.TYPE_MOVE:
                        PointerInfo info = MouseInfo.getPointerInfo();
                        if (info != null) {
                            Point point = info.getLocation();
                            mRobot.mouseMove((int) (event.x * mMaxMotionDist) + point.x,
                                    (int) (event.y * mMaxMotionDist) + point.y);
                        }
                        break;
                    case MouseEvent.TYPE_PRESS:
                        mRobot.mousePress(event.button);
                        break;
                    case MouseEvent.TYPE_RELEASE:
                        mRobot.mouseRelease(event.button);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            STD_OUT.println("Failed to launch the server. Is there already a running server?");
        }
    }

    private void printLog(String str) {
        if (DEBUG_MODE) {
            STD_OUT.print(str);
        }
    }

    private void printlnLog(String str) {
        if (DEBUG_MODE) {
            STD_OUT.println(str);
        }
    }

    public static int convertButtonMask(int button) {
        switch (button) {
        case PACKET_MOUSE_BUTTON_LEFT:
            return InputEvent.BUTTON1_MASK;
        case PACKET_MOUSE_BUTTON_RIGHT:
            return InputEvent.BUTTON3_MASK;
        case PACKET_MOUSE_BUTTON_MIDDLE:
            return InputEvent.BUTTON3_DOWN_MASK;
        default:
            return -1;
        }
    }
    public static void main(String[] args) throws InterruptedException, AWTException, IOException {
        Server server = new Server();
        server.start();
    }
}
