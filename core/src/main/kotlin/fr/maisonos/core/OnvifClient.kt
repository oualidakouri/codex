package fr.maisonos.core

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.Proxy
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.xml.parsers.DocumentBuilderFactory

object SafeXml {
    fun parse(xml: String): Element {
        require(xml.toByteArray().size <= 1_048_576)
        // Android's DOM implementation does not support all Xerces feature flags.
        // Reject every DTD/entity declaration before parsing, regardless of parser support.
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "DTD/entities prohibited" }
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val builder = f.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entities prohibited") }
        return builder.parse(ByteArrayInputStream(xml.toByteArray())).documentElement
    }
    fun all(root: Element, name: String): List<Element> = root.getElementsByTagNameNS("*", name).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
    fun text(root: Element, name: String) = all(root, name).firstOrNull()?.textContent.orEmpty()
    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}

data class OnvifSession(val mediaUrl: String, val ptzUrl: String?, val profile: String, val streamUrl: String?, val continuousPanTilt: Boolean)

/** ONVIF Profile S subset: capability discovery, first profile, RTSP URI, bounded PTZ. */
class OnvifClient(private val config: CameraConfig, sockets: SocketFactory = SocketFactory.getDefault()) : PtzPort {
    private val client = OkHttpClient.Builder().socketFactory(sockets).proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
        .authenticator { _, response -> digest(response) }.build()
    private var session: OnvifSession? = null
    fun inspect(): OnvifSession {
        config.validate()
        val caps = soap("http://${config.host}:${config.onvifPort}${config.onvifPath}", "http://www.onvif.org/ver10/device/wsdl/GetCapabilities", "<tds:GetCapabilities><tds:Category>All</tds:Category></tds:GetCapabilities>")
        val media = SafeXml.all(caps, "Media").firstOrNull()?.let { SafeXml.text(it, "XAddr") }?.takeIf { it.isNotBlank() } ?: error("Service média ONVIF absent")
        val ptz = SafeXml.all(caps, "PTZ").firstOrNull()?.let { SafeXml.text(it, "XAddr") }?.takeIf { it.isNotBlank() }
        LocalEndpoint.requireUrl(media, setOf("http", "https"), config.host)
        ptz?.let { LocalEndpoint.requireUrl(it, setOf("http", "https"), config.host) }
        val profiles = soap(media, "http://www.onvif.org/ver10/media/wsdl/GetProfiles", "<trt:GetProfiles/>")
        val profile = SafeXml.all(profiles, "Profiles").minByOrNull { item ->
            val width = SafeXml.text(item, "Width").toLongOrNull()
            val height = SafeXml.text(item, "Height").toLongOrNull()
            if (width != null && height != null && width in 1..16384 && height in 1..16384) width * height else Long.MAX_VALUE
        } ?: error("Aucun profil vidéo ONVIF")
        val token = profile.getAttribute("token").also { require(it.isNotBlank()) }
        val stream = runCatching {
            val r = soap(media, "http://www.onvif.org/ver10/media/wsdl/GetStreamUri", "<trt:GetStreamUri><trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trt:StreamSetup><trt:ProfileToken>${SafeXml.escape(token)}</trt:ProfileToken></trt:GetStreamUri>")
            LocalEndpoint.withoutRtspCredentials(SafeXml.text(r, "Uri"), config.host)
        }.getOrNull()
        val continuous = ptz != null && SafeXml.all(profile, "PTZConfiguration").isNotEmpty() && runCatching {
            val nodes = soap(ptz, "http://www.onvif.org/ver20/ptz/wsdl/GetNodes", "<tptz:GetNodes/>")
            SafeXml.all(nodes, "ContinuousPanTiltVelocitySpace").isNotEmpty()
        }.getOrDefault(false)
        return OnvifSession(media, ptz, token, stream, continuous).also { session = it }
    }
    fun move(x: Double, y: Double) {
        require(x in -1.0..1.0 && y in -1.0..1.0)
        val s = checkNotNull(session); check(s.continuousPanTilt)
        soap(checkNotNull(s.ptzUrl), "http://www.onvif.org/ver20/ptz/wsdl/ContinuousMove", "<tptz:ContinuousMove><tptz:ProfileToken>${SafeXml.escape(s.profile)}</tptz:ProfileToken><tptz:Velocity><tt:PanTilt x=\"$x\" y=\"$y\"/></tptz:Velocity><tptz:Timeout>PT0.5S</tptz:Timeout></tptz:ContinuousMove>")
    }
    override fun move(direction: PanTilt, speed: Double) { require(speed in 0.0..1.0); move(direction.x * speed, direction.y * speed) }
    override fun stop() {
        val s = session ?: return
        if (!s.continuousPanTilt) return
        soap(checkNotNull(s.ptzUrl), "http://www.onvif.org/ver20/ptz/wsdl/Stop", "<tptz:Stop><tptz:ProfileToken>${SafeXml.escape(s.profile)}</tptz:ProfileToken><tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom></tptz:Stop>")
    }
    private fun soap(url: String, action: String, body: String): Element {
        LocalEndpoint.requireUrl(url, setOf("http", "https"), config.host)
        val envelope = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns:trt="http://www.onvif.org/ver10/media/wsdl" xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema"><s:Header>${security()}</s:Header><s:Body>$body</s:Body></s:Envelope>"""
        val request = Request.Builder().url(url).post(envelope.toRequestBody("application/soap+xml; charset=utf-8; action=\"$action\"".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw CameraProtocolFailure(when (response.code) { 401,403 -> CameraFailureKind.AUTHENTICATION; 404 -> CameraFailureKind.ONVIF_PATH; else -> CameraFailureKind.PROTOCOL }, response.code)
            val source = checkNotNull(response.body).source()
            source.request(1_048_577)
            require(source.buffer.size <= 1_048_576) { "Réponse ONVIF trop volumineuse" }
            val root = SafeXml.parse(source.readUtf8())
            if (SafeXml.all(root, "Fault").isNotEmpty()) throw CameraProtocolFailure(CameraFailureKind.ONVIF_FAULT)
            return root
        }
    }
    private fun security(): String {
        val nonce = ByteArray(20).also(SecureRandom()::nextBytes)
        val created = Instant.now().toString()
        val digest = MessageDigest.getInstance("SHA-1").digest(nonce + created.toByteArray() + config.password.toByteArray())
        val b64 = Base64.getEncoder()
        return """<wsse:Security s:mustUnderstand="1" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"><wsse:UsernameToken><wsse:Username>${SafeXml.escape(config.username)}</wsse:Username><wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">${b64.encodeToString(digest)}</wsse:Password><wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">${b64.encodeToString(nonce)}</wsse:Nonce><wsu:Created>$created</wsu:Created></wsse:UsernameToken></wsse:Security>"""
    }
    private fun digest(response: Response): Request? {
        if (response.request.header("Authorization") != null) return null
        val challenge = response.challenges().firstOrNull { it.scheme.equals("Digest", true) } ?: return null
        val p = challenge.authParams
        val realm = p["realm"] ?: return null
        val nonce = p["nonce"] ?: return null
        val algorithm = p["algorithm"] ?: "MD5"
        if (algorithm.uppercase() !in setOf("MD5", "SHA-256")) return null
        val qop = p["qop"]
        if (qop != null && "auth" !in qop.split(',').map(String::trim)) return null
        val uri = response.request.url.encodedPath + (response.request.url.encodedQuery?.let { "?$it" } ?: "")
        val cn = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        fun hash(v: String) = MessageDigest.getInstance(algorithm).digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
        fun quote(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
        val ha1 = hash("${config.username}:$realm:${config.password}")
        val ha2 = hash("${response.request.method}:$uri")
        val result = hash(if (qop != null) "$ha1:$nonce:00000001:$cn:auth:$ha2" else "$ha1:$nonce:$ha2")
        val auth = "Digest username=\"${quote(config.username)}\", realm=\"${quote(realm)}\", nonce=\"${quote(nonce)}\", uri=\"${quote(uri)}\", response=\"$result\", algorithm=$algorithm" +
            (if (qop != null) ", qop=auth, nc=00000001, cnonce=\"$cn\"" else "") + (p["opaque"]?.let { ", opaque=\"${quote(it)}\"" } ?: "")
        return response.request.newBuilder().header("Authorization", auth).build()
    }
    override fun close() { client.dispatcher.cancelAll(); client.connectionPool.evictAll() }
}
