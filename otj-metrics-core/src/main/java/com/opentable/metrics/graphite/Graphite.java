/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.metrics.graphite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.codahale.metrics.graphite.GraphiteSender;

/**
 * A client to a Carbon server via TCP.
 *
 * Note: This is a copy/paste from DropWizard we want to get rid of as soon as we diagnose the socket issues.
 */
public class Graphite implements GraphiteSender {
    // this may be optimistic about Carbon/Graphite

    private final String hostname;
    private final int port;
    private final InetSocketAddress address;
    private final SocketFactory socketFactory;
    private final Charset charset;

    private Socket socket;
    private Writer writer;
    private int failures;

    private static final Logger LOGGER = LoggerFactory.getLogger(Graphite.class);

    /**
     * Creates a new client which connects to the given address using the default
     * {@link SocketFactory}.
     *
     * @param hostname The hostname of the Carbon server
     * @param port     The port of the Carbon server
     */
    public Graphite(String hostname, int port) {
        this(hostname, port, SocketFactory.getDefault());
    }

    /**
     * Creates a new client which connects to the given address and socket factory.
     *
     * @param hostname      The hostname of the Carbon server
     * @param port          The port of the Carbon server
     * @param socketFactory the socket factory
     */
    public Graphite(String hostname, int port, SocketFactory socketFactory) {
        this(hostname, port, socketFactory, UTF_8);
    }

    /**
     * Creates a new client which connects to the given address and socket factory using the given
     * character set.
     *
     * @param hostname      The hostname of the Carbon server
     * @param port          The port of the Carbon server
     * @param socketFactory the socket factory
     * @param charset       the character set used by the server
     */
    public Graphite(String hostname, int port, SocketFactory socketFactory, Charset charset) {
        this.hostname = hostname;
        this.port = port;
        this.address = null;
        this.socketFactory = socketFactory;
        this.charset = charset;
    }

    /**
     * Creates a new client which connects to the given address using the default
     * {@link SocketFactory}.
     *
     * @param address the address of the Carbon server
     */
    public Graphite(InetSocketAddress address) {
        this(address, SocketFactory.getDefault());
    }

    /**
     * Creates a new client which connects to the given address and socket factory.
     *
     * @param address       the address of the Carbon server
     * @param socketFactory the socket factory
     */
    public Graphite(InetSocketAddress address, SocketFactory socketFactory) {
        this(address, socketFactory, UTF_8);
    }

    /**
     * Creates a new client which connects to the given address and socket factory using the given
     * character set.
     *
     * @param address       the address of the Carbon server
     * @param socketFactory the socket factory
     * @param charset       the character set used by the server
     */
    public Graphite(InetSocketAddress address, SocketFactory socketFactory, Charset charset) {
        this.hostname = null;
        this.port = -1;
        this.address = address;
        this.socketFactory = socketFactory;
        this.charset = charset;
    }

    @Override
    public void connect() throws IllegalStateException, IOException {
        if (isConnected()) {
            throw new IllegalStateException("Already connected");
        }
        InetSocketAddress address = this.address;
        if (address == null) {
            address = new InetSocketAddress(hostname, port);
        }
        if (address.getAddress() == null) {
            // retry lookup, just in case the DNS changed
            address = new InetSocketAddress(address.getHostName(), address.getPort());

            if (address.getAddress() == null) {
                throw new UnknownHostException(address.getHostName());
            }
        }

        try {
            this.socket = socketFactory.createSocket(address.getAddress(), address.getPort());
        } catch (IOException e) {
            LOGGER.error("PLEASE REPORT TO ARCHTEAM",e);
            LOGGER.error("ADDITIONAL INFO: host {}, port {}, addr.addr {},addr.host {}, addr.port {}",
                    hostname, port, address.getAddress(), address.getHostName(), address.getPort());
            throw e;
        }
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), charset));
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void send(String name, String value, long timestamp) throws IOException {
        try {
            writer.write(sanitize(name));
            writer.write(' ');
            writer.write(sanitize(value));
            writer.write(' ');
            writer.write(Long.toString(timestamp));
            writer.write('\n');
            this.failures = 0;
        } catch (IOException e) {
            failures++;
            throw e;
        }
    }

    @Override
    public int getFailures() {
        return failures;
    }

    @Override
    public void flush() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ex) {
            LOGGER.debug("Error closing writer", ex);
        } finally {
            this.writer = null;
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ex) {
            LOGGER.debug("Error closing socket", ex);
        } finally {
            this.socket = null;
        }
    }
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final String DASH = "-";

    /**
     * Trims the string and replaces all whitespace characters with the provided symbol
     */
    private static String sanitize(String string) {
        return WHITESPACE.matcher(string.trim()).replaceAll(DASH);
    }
}
