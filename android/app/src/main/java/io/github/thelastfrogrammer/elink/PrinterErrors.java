package io.github.thelastfrogrammer.elink;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.eclipse.paho.client.mqttv3.MqttException;

/** Never display raw transport exceptions: they can contain a credential-bearing URL. */
public final class PrinterErrors {
    private PrinterErrors() { }
    public static final class Rejected extends IOException {
        public final int code;
        public Rejected(int code) { super(code(code)); this.code = code; }
    }
    public static final class HttpStatus extends IOException {
        public final int status;
        public HttpStatus(int status) { this.status = status; }
    }
    public static final class MissingIdentity extends IOException { }
    public static final class IdentityUnavailable extends IOException { }
    public static final class CloudMode extends IOException { }
    public static final class ModeMismatch extends IOException { }
    public static String code(int code) {
        String reason;
        switch (code) {
            case 1000: reason = "Access code rejected. Check the code under Settings → LAN Only"; break;
            case 1001: reason = "This firmware does not support that request"; break;
            case 1002: reason = "Printer could not open the folder"; break;
            case 1003: reason = "Printer rejected the request parameters"; break;
            case 1004: case 9002: reason = "Printer could not write the file"; break;
            case 1009: reason = "Printer is busy"; break;
            case 1010: reason = "Printer is not currently printing"; break;
            case 1012: reason = "Print task was not found"; break;
            case 9000: reason = "Upload offset mismatch. Check for a partial file before uploading again"; break;
            case 9004: case 9008: case 9009: reason = "Upload checksum failed"; break;
            case -1: reason = "Printer response is missing an error code"; break;
            default: reason = "Printer rejected the request";
        }
        return reason + " (code " + code + ").";
    }
    public static String describe(Throwable error, String stage) {
        Throwable routeError = error;
        for (int i = 0; i < 8 && routeError != null; i++, routeError = routeError.getCause()) if (routeError instanceof VpnRouteGuard.Unavailable) return new VpnRouteGuard.Unavailable().getMessage();
        if (error instanceof ModeMismatch) return "Printer reports LAN Only, but Cloud-mode PIN probe was selected. To test Matrix coexistence, leave the printer in cloud mode; otherwise select LAN access code in this app.";
        if (error instanceof CloudMode) return "The printer reports cloud / WAN mode, but LAN access code was selected. To preserve Matrix, try the separate Cloud-mode PIN probe (read-only, experimental). LAN Only remains an optional alternative; a LAN code is not a pairing PIN.";
        if (error instanceof IdentityUnavailable) return "Could not obtain the printer serial through UDP discovery or HTTP. Enter the exact Serial Number from Settings → Device in the optional serial field, then connect over MQTT.";
        if (error instanceof Rejected) return error.getMessage();
        if (error instanceof HttpStatus) return stage + " replied with HTTP " + ((HttpStatus) error).status + ". Verify that this IP belongs to the CC2 and that its firmware exposes /system/info.";
        if (error instanceof MissingIdentity) return "HTTP connected, but the response did not contain the printer serial number. Check the IP and CC2 firmware/API compatibility.";
        if (error instanceof org.json.JSONException) return stage + " returned unexpected data. Verify that the IP belongs to the CC2, rather than another local device.";
        if (error instanceof MqttException) {
            int code = ((MqttException) error).getReasonCode();
            if (code == 4) return "MQTT username or password was not accepted (code 4). Check the current LAN access code and code-protection setting.";
            if (code == 5) return stage + " was not authorized (MQTT code 5). The printer does not specify which authorization rule failed. Verify LAN Only and its current access code; code protection off uses the default credential.";
            return "MQTT connection failed (code " + code + "). Run Check connection; verify LAN Only and MQTT port 1883.";
        }
        Throwable cause = error;
        for (int i = 0; i < 8 && cause != null; i++, cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof NoRouteToHostException)
                return stage + " could not reach the printer. Check its current IP, LAN Only, and the selected connection route. Local mode needs home Wi-Fi; remote mode needs a working home VPN/subnet route.";
            if (cause instanceof SocketTimeoutException)
                return stage + " timed out. Run Check connection to distinguish Wi-Fi reachability from printer authentication.";
        }
        if (error instanceof TimeoutException) return stage.equals("Printer registration")
            ? "Printer registration timed out. MQTT connected, but no usable registration reply arrived for this client. This does not establish PIN validity or firmware support."
            : stage + " timed out.";
        return stage + " failed. Run Check connection and compare the IP with the printer's Network screen.";
    }
    public static boolean retryable(Throwable error) {
        if (error instanceof Rejected || error instanceof IllegalArgumentException || error instanceof IllegalStateException) return false;
        if (error instanceof MissingIdentity || error instanceof IdentityUnavailable || error instanceof CloudMode || error instanceof ModeMismatch || error instanceof org.json.JSONException) return false;
        if (error instanceof HttpStatus) return ((HttpStatus) error).status >= 500;
        if (error instanceof MqttException) {
            int code = ((MqttException) error).getReasonCode();
            return code != 4 && code != 5;
        }
        return true;
    }
}
