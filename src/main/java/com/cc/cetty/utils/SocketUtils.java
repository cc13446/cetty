package com.cc.cetty.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public class SocketUtils {

    public static void bind(final SocketChannel socketChannel, final SocketAddress address) throws IOException {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                socketChannel.bind(address);
                return null;
            });
        } catch (PrivilegedActionException e) {
            throw (IOException) e.getCause();
        }
    }

    public static boolean connect(final SocketChannel socketChannel, final SocketAddress remoteAddress)
            throws IOException {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> socketChannel.connect(remoteAddress));
        } catch (PrivilegedActionException e) {
            throw (IOException) e.getCause();
        }
    }

    public static SocketAddress localSocketAddress(final ServerSocket socket) {
        return AccessController.doPrivileged((PrivilegedAction<SocketAddress>) socket::getLocalSocketAddress);
    }

    public static SocketChannel accept(final ServerSocketChannel serverSocketChannel) throws IOException {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<SocketChannel>) serverSocketChannel::accept);
        } catch (PrivilegedActionException e) {
            throw (IOException) e.getCause();
        }
    }

    public static InetSocketAddress socketAddress(final String hostname, final int port) {
        return AccessController.doPrivileged((PrivilegedAction<InetSocketAddress>) () -> new InetSocketAddress(hostname, port));
    }
}
