/*
    This file is part of Peers, a java SIP softphone.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    
    Copyright 2010-2013 Yohann Martineau 
*/

package dk.apaq.peers.rtp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import dk.apaq.peers.media.MediaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * can be instantiated on UAC INVITE sending or on UAS 200 OK sending 
 */
public class RtpSession {

    private static final Logger LOG = LoggerFactory.getLogger(RtpSession.class);
    private InetAddress remoteAddress;
    private int remotePort;
    private DatagramSocket datagramSocket;
    private ExecutorService executorService;
    private List<RtpListener> rtpListeners;
    private RtpParser rtpParser;
    private FileOutputStream rtpSessionOutput;
    private FileOutputStream rtpSessionInput;
    private boolean mediaDebug;
    private String peersHome;

    public RtpSession(InetAddress localAddress, DatagramSocket datagramSocket, boolean mediaDebug, String peersHome) {
        this.mediaDebug = mediaDebug;
        this.peersHome = peersHome;
        this.datagramSocket = datagramSocket;
        rtpListeners = new ArrayList<RtpListener>();
        rtpParser = new RtpParser();
        executorService = Executors.newSingleThreadExecutor();
    }

    public synchronized void start() {
        if (mediaDebug) {
            SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String date = simpleDateFormat.format(new Date());
            String dir = peersHome + File.separator + MediaManager.MEDIA_DIR
                + File.separator;
            String fileName = dir + date + "_rtp_session.output";
            try {
                rtpSessionOutput = new FileOutputStream(fileName);
                fileName = dir + date + "_rtp_session.input";
                rtpSessionInput = new FileOutputStream(fileName);
            } catch (FileNotFoundException e) {
                LOG.error("cannot create file", e);
                return;
            }
        }
        executorService.submit(new Receiver());
    }

    public void stop() {
        executorService.shutdown();
    }

    public void addRtpListener(RtpListener rtpListener) {
        rtpListeners.add(rtpListener);
    }

    public synchronized void send(RtpPacket rtpPacket) {
        if (datagramSocket == null) {
            return;
        }
        byte[] buf = rtpParser.encode(rtpPacket);
        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length,
                remoteAddress, remotePort);
        
        if (!datagramSocket.isClosed()) {
            try {
                datagramSocket.send(datagramPacket);
            } catch (IOException e) {
                LOG.error("cannot send rtp packet", e);
            }
            if (mediaDebug) {
                try {
                    rtpSessionOutput.write(buf);
                } catch (IOException e) {
                    LOG.error("cannot write to file", e);
                }
            }
        }
    }

    public void setRemoteAddress(InetAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    private void closeFileAndDatagramSocket() {
        if (mediaDebug) {
            try {
                rtpSessionOutput.close();
                rtpSessionInput.close();
            } catch (IOException e) {
                LOG.error("cannot close file", e);
            }
        }
        datagramSocket.close();
    }

    class Receiver implements Runnable {

        @Override
        public void run() {
            int receiveBufferSize;
            try {
                receiveBufferSize = datagramSocket.getReceiveBufferSize();
            } catch (SocketException e) {
                LOG.error("cannot get datagram socket receive buffer size",
                        e);
                return;
            }
            byte[] buf = new byte[receiveBufferSize];
            DatagramPacket datagramPacket = new DatagramPacket(buf,
                    buf.length);
            try {
                datagramSocket.receive(datagramPacket);
            } catch (SocketTimeoutException e) {
                try {
                    executorService.execute(this);
                } catch (RejectedExecutionException rej) {
                    closeFileAndDatagramSocket();
                }
                return;
            } catch (IOException e) {
                LOG.error("cannot receive packet", e);
                return;
            }
            InetAddress remoteAddress = datagramPacket.getAddress();
            if (remoteAddress != null &&
                    !remoteAddress.equals(RtpSession.this.remoteAddress)) {
                RtpSession.this.remoteAddress = remoteAddress;
            }
            int remotePort = datagramPacket.getPort();
            if (remotePort != RtpSession.this.remotePort) {
                RtpSession.this.remotePort = remotePort;
            }
            byte[] data = datagramPacket.getData();
            int offset = datagramPacket.getOffset();
            int length = datagramPacket.getLength();
            byte[] trimmedData = new byte[length];
            System.arraycopy(data, offset, trimmedData, 0, length);
            if (mediaDebug) {
                try {
                    rtpSessionInput.write(trimmedData);
                } catch (IOException e) {
                    LOG.error("cannot write to file", e);
                    return;
                }
            }
            RtpPacket rtpPacket = rtpParser.decode(trimmedData);
            for (RtpListener rtpListener: rtpListeners) {
                rtpListener.receivedRtpPacket(rtpPacket);
            }
            try {
                executorService.execute(this);
            } catch (RejectedExecutionException rej) {
                closeFileAndDatagramSocket();
            }
        }

    }

    public boolean isSocketClosed() {
        if (datagramSocket == null) {
            return true;
        }
        return datagramSocket.isClosed();
    }

}
