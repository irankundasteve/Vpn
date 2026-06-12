package com.example

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Processes packets from the VPN interface and routes them through a proxy server.
 * Handles IP packet parsing and traffic forwarding.
 */
class VpnPacketProcessor(
    private val vpnInterface: ParcelFileDescriptor,
    private val proxyHost: String,
    private val proxyPort: Int
) {
    private val inputStream = FileInputStream(vpnInterface.fileDescriptor)
    private val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
    private val packetBuffer = ByteArray(32768) // Standard MTU size
    private val proxyConnections = mutableMapOf<String, Socket>()

    companion object {
        private const val TAG = "VpnPacketProcessor"
        // IP protocol numbers
        private const val PROTOCOL_TCP = 6
        private const val PROTOCOL_UDP = 17
        private const val PROTOCOL_ICMP = 1
    }

    suspend fun startProcessing() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting packet processor - routing traffic through $proxyHost:$proxyPort")
        
        try {
            while (true) {
                // Read packet from TUN interface
                val length = inputStream.read(packetBuffer)
                if (length <= 0) continue

                val packet = packetBuffer.copyOf(length)
                processPacket(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet processing error: ${e.message}", e)
        }
    }

    private suspend fun processPacket(packet: ByteArray) = withContext(Dispatchers.IO) {
        if (packet.size < 20) return@withContext // Minimum IP header size

        try {
            val buffer = ByteBuffer.wrap(packet)
            val versionAndLength = buffer.get().toInt() and 0xFF
            val version = (versionAndLength shr 4) and 0x0F
            val headerLength = ((versionAndLength and 0x0F) * 4)
            
            if (version != 4 || headerLength > packet.size) return@withContext

            // Extract IP header fields
            buffer.position(9)
            val protocol = buffer.get().toInt() and 0xFF
            
            // Extract source and destination IPs
            buffer.position(12)
            val srcIp = InetAddress.getByAddress(ByteArray(4).apply { buffer.get(this) }).hostAddress
            val dstIp = InetAddress.getByAddress(ByteArray(4).apply { buffer.get(this) }).hostAddress

            when (protocol) {
                PROTOCOL_TCP -> processTcpPacket(packet, srcIp, dstIp, headerLength)
                PROTOCOL_UDP -> processUdpPacket(packet, srcIp, dstIp, headerLength)
                PROTOCOL_ICMP -> processIcmpPacket(packet, srcIp, dstIp)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing packet: ${e.message}")
        }
    }

    private suspend fun processTcpPacket(
        packet: ByteArray,
        srcIp: String,
        dstIp: String,
        headerLength: Int
    ) = withContext(Dispatchers.IO) {
        if (packet.size < headerLength + 20) return@withContext

        try {
            val buffer = ByteBuffer.wrap(packet)
            buffer.position(headerLength)
            
            val srcPort = ((buffer.get().toInt() and 0xFF) shl 8) or (buffer.get().toInt() and 0xFF)
            val dstPort = ((buffer.get().toInt() and 0xFF) shl 8) or (buffer.get().toInt() and 0xFF)
            
            Log.d(TAG, "TCP: $srcIp:$srcPort -> $dstIp:$dstPort")
            
            // Forward TCP traffic through proxy
            forwardTcpToProxy(packet, dstIp, dstPort, srcIp, srcPort)
        } catch (e: Exception) {
            Log.w(TAG, "TCP processing error: ${e.message}")
        }
    }

    private suspend fun forwardTcpToProxy(
        packet: ByteArray,
        dstIp: String,
        dstPort: Int,
        srcIp: String,
        srcPort: Int
    ) = withContext(Dispatchers.IO) {
        val connectionKey = "$srcIp:$srcPort"
        
        try {
            var proxySocket = proxyConnections[connectionKey]
            
            if (proxySocket == null) {
                // Create new connection to proxy
                proxySocket = Socket()
                proxySocket.connect(java.net.InetSocketAddress(proxyHost, proxyPort), 5000)
                proxyConnections[connectionKey] = proxySocket
                Log.d(TAG, "Established proxy connection: $connectionKey -> $proxyHost:$proxyPort")
            }

            // Extract TCP payload (data after headers)
            val tcpHeaderStart = 20 // IP header
            val tcpHeaderSize = ((packet[tcpHeaderStart].toInt() and 0xF0) shr 4) * 4
            val payloadStart = tcpHeaderStart + tcpHeaderSize

            if (payloadStart < packet.size) {
                val payload = packet.copyOfRange(payloadStart, packet.size)
                proxySocket.getOutputStream().write(payload)
                proxySocket.getOutputStream().flush()
                
                Log.d(TAG, "Forwarded ${payload.size} bytes through proxy")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proxy forwarding error: ${e.message}")
            proxyConnections.remove(connectionKey)?.close()
        }
    }

    private suspend fun processUdpPacket(
        packet: ByteArray,
        srcIp: String,
        dstIp: String,
        headerLength: Int
    ) = withContext(Dispatchers.IO) {
        if (packet.size < headerLength + 8) return@withContext

        try {
            val buffer = ByteBuffer.wrap(packet)
            buffer.position(headerLength)
            
            val srcPort = ((buffer.get().toInt() and 0xFF) shl 8) or (buffer.get().toInt() and 0xFF)
            val dstPort = ((buffer.get().toInt() and 0xFF) shl 8) or (buffer.get().toInt() and 0xFF)
            
            Log.d(TAG, "UDP: $srcIp:$srcPort -> $dstIp:$dstPort")
            
            // Handle DNS (port 53) specially
            if (dstPort == 53) {
                processDnsQuery(packet, srcIp, srcPort, dstIp, dstPort, headerLength)
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP processing error: ${e.message}")
        }
    }

    private suspend fun processDnsQuery(
        packet: ByteArray,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        headerLength: Int
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "DNS query from $srcIp:$srcPort")
            // DNS queries can be forwarded through proxy or handled locally
            // For now, we log the attempt
        } catch (e: Exception) {
            Log.w(TAG, "DNS processing error: ${e.message}")
        }
    }

    private suspend fun processIcmpPacket(
        packet: ByteArray,
        srcIp: String,
        dstIp: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "ICMP: $srcIp -> $dstIp (ping detected)")
    }

    suspend fun shutdown() = withContext(Dispatchers.IO) {
        try {
            proxyConnections.values.forEach { it.close() }
            proxyConnections.clear()
            inputStream.close()
            outputStream.close()
            Log.d(TAG, "Packet processor shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown: ${e.message}")
        }
    }
}
