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
    
    Copyright 2007, 2008, 2009, 2010 Yohann Martineau 
*/

package dk.apaq.peers.media;

import java.io.IOException;

import dk.apaq.peers.rtp.RFC3551;
import dk.apaq.peers.rtp.RtpListener;
import dk.apaq.peers.rtp.RtpPacket;
import dk.apaq.peers.rtp.RtpSession;
import dk.apaq.peers.sdp.Codec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncomingRtpReader implements RtpListener {

    private static final Logger LOG = LoggerFactory.getLogger(IncomingRtpReader.class);
    private RtpSession rtpSession;
    private SoundManager soundManager;
    private Decoder decoder;

    public IncomingRtpReader(RtpSession rtpSession, SoundManager soundManager, Codec codec)
            throws IOException {
        LOG.debug("playback codec:" + codec.toString().trim());
        this.rtpSession = rtpSession;
        this.soundManager = soundManager;
        switch (codec.getPayloadType()) {
        case RFC3551.PAYLOAD_TYPE_PCMU:
            decoder = new PcmuDecoder();
            break;
        case RFC3551.PAYLOAD_TYPE_PCMA:
            decoder = new PcmaDecoder();
            break;
        default:
            throw new RuntimeException("unsupported payload type");
        }
        rtpSession.addRtpListener(this);
    }
    
    public void start() {
        rtpSession.start();
    }

    @Override
    public void receivedRtpPacket(RtpPacket rtpPacket) {
        byte[] rawBuf = decoder.process(rtpPacket.getData());
        if (soundManager != null) {
            soundManager.writeData(rawBuf, 0, rawBuf.length);
        }
    }

}
