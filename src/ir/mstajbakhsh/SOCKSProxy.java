package ir.mstajbakhsh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class SOCKSProxy extends OnionProxy {
    Thread innerToHiddenThread;
    Thread hiddenToInnerThread;
    Thread serverThread;
    private ServerSocket serverSocket;
    boolean debug = false;

    public SOCKSProxy(String Host, int Port, ITunnelStatus eventHandler) {
        super(Host, Port, eventHandler);
    }

    private static boolean available(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return false;
        } catch (IOException ignored) {
            return true;
        }
    }

    @Override
    public void createInnerHandler() {
        this._Host = "127.0.0.1";
        Random r = new Random();
        while (serverSocket == null) {
            try {
                _Port = r.nextInt(5000) + 10000;
                if (available(_Port)) {
                    break;
                }
            } catch (Exception ex) {
                continue;
            }
        }
        startServer();
    }

    public void startServer() {
        try {
            if (serverSocket != null && serverSocket.isBound()) {
                serverSocket.close();
                eventHandler.onConnectionClosed(this);
            }

            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
            }
            serverSocket = new ServerSocket(_Port);
        } catch (Exception ex) {

        }
        //Server socket is ready!
        eventHandler.onConnectionPrepared(this);

        Address normal = new Address(_Host, _Port);
        Address hidden = new Address(_HiddenHost, _HiddenPort);
        Variable.local2onion.put(normal, hidden);
        Variable.onion2local.put(hidden, normal);

        //Connect to hiddenService
        InetSocketAddress hiddenSA = InetSocketAddress.createUnresolved(_HiddenHost, _HiddenPort);
        Proxy p = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(Variable.localTorIP, Variable.localTorPort));
        Socket hiddenSocket = new Socket(p);

        Runnable serverRunnable = () -> {
            try {
                while (true) {
                    Socket innerSocket = serverSocket.accept();
                    Log(String.format("Socket connection from port %d", innerSocket.getPort()));
                    if (!hiddenSocket.isConnected()) {
                        hiddenSocket.connect(hiddenSA);
                    }
                    prepareAndStartTunnelThreads(innerSocket, hiddenSocket);
                    eventHandler.onConnectionEstablished(this);
                }
            } catch (IOException ex) {
                eventHandler.onExceptionOccured(this, ex);
            }
        };
        serverThread = new Thread(serverRunnable, "Server");
        serverThread.start();
    }

    public boolean find(byte[] buffer, byte[] key) {
        for (int i = 0; i <= buffer.length - key.length; i++) {
            int j = 0;
            while (j < key.length && buffer[i + j] == key[j]) {
                j++;
            }
            if (j == key.length) {
                return true;
            }
        }
        return false;
    }

    public boolean findHeader(byte[] buffer) {
        return true;
    }

    public String getHeader(String buffer) {
        String empty = new String("");
        String sentinel = new String("\r\n\r\n");
        int index = buffer.indexOf(sentinel);
        if (index == -1) {
            return empty;
        }
        return buffer.substring(0, index);
    }

    public byte[] replaceHost(String buffer, String host, Socket socket, int counter) {
        byte[] empty = new byte[0];
        String sentinel = new String("HOST: ");
        int index = buffer.toUpperCase(Locale.ROOT).indexOf(sentinel);
        if (index == -1) {
            return buffer.getBytes();
        }
        index += sentinel.length();
        String localhost = buffer.substring(index, buffer.indexOf("\r\n", index));
        //Log(String.format("Replacing %s with %s", localhost, host));
        buffer = buffer.replace(localhost, host);
        if (debug) {
            Log(String.format("######## REQUEST %d:%d\n%s",
                    socket.getPort(), counter, buffer));
            Log(String.format("######## REQUEST %d:%d End (%d bytes)",
                    socket.getPort(), counter, buffer.length()));
        }
        return buffer.getBytes();
    }

    public void Log(String message) {
        System.out.println("[" + _id + "] " + message);
    }

    @Override
    public void prepareAndStartTunnelThreads(Socket innerSocket, Socket hiddenSocket) {
        Runnable innerToHidden = () -> {
            int requestCounter = 0;
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
            try {
                InputStream inputStream = innerSocket.getInputStream();
                OutputStream outputStream = hiddenSocket.getOutputStream();
                while (!innerSocket.isInputShutdown() && !hiddenSocket.isOutputShutdown()) {
                    int bytesRead = inputStream.read(buffer);
                    requestCounter++;
                    if (bytesRead == -1) {
                        break;
                    }
                    bufferStream.write(buffer, 0, bytesRead);
                    byte[] buf = replaceHost(bufferStream.toString(), _HiddenHost,
                                             innerSocket, requestCounter);
                    outputStream.write(buf);
                    outputStream.flush();
                    bufferStream.reset();
                }
            } catch (IOException ex) {
                Log(String.format("Failed writing REQUEST from port %d", innerSocket.getPort()));
                ex.printStackTrace();
                //eventHandler.onExceptionOccured(this, ex);
                //startServer();
            }
        };
        innerToHiddenThread = new Thread(innerToHidden);

        Runnable hiddenToInner = () -> {
            int responseCounter = 0;
            ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[1024];
                InputStream inputStream = hiddenSocket.getInputStream();
                OutputStream outputStream = innerSocket.getOutputStream();
                while (!hiddenSocket.isInputShutdown() && !innerSocket.isOutputShutdown()) {
                    int bytesRead = inputStream.read(buffer);
                    responseCounter++;
                    if (bytesRead == -1) {
                        break;
                    }
                    if (debug) {
                        bufferStream.write(buffer, 0, bytesRead);
                        String responseInfo = new String("");
                        String header = getHeader(responseInfo);
                        String responseTag = String.format("######## RESPONSE %d:%d",
                                innerSocket.getPort(), responseCounter);
                        if (header.length() > 0) {
                            responseInfo =  "\n" + header;
                        }
                        Log(responseTag + responseInfo);
                        String responseEndTag = String.format("######## RESPONSE %d:%d End",
                                innerSocket.getPort(), responseCounter);
                        responseEndTag += String.format(" (%d bytes of data not shown)",
                                buffer.length - header.length());
                        Log(responseEndTag);
                        outputStream.write(bufferStream.toByteArray());
                    }
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                    bufferStream.reset();
                }
            } catch (IOException ex) {
                Log(String.format("Failed writing RESPONSE on port %d", innerSocket.getPort()));
                ex.printStackTrace();
                //eventHandler.onExceptionOccured(this, ex);
                //startServer();
            }
        };
        //hiddenToInnerThread = new Thread(hiddenToInner, _HiddenHost + "2" + _Port);
        hiddenToInnerThread = new Thread(hiddenToInner);

        innerToHiddenThread.start();
        hiddenToInnerThread.start();
    }

    @Override
    public String getProxyType() {
        return "SOCKS";
    }
}
