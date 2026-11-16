package cn.iocoder.yudao.module.campus.framework.esp32;

import lombok.Getter;

import java.util.regex.Pattern;

/**
 * 由网关直接处理的语音指令（调音量、别说了）。
 *
 * <p>只匹配很短、意图明确的整句，避免把正常聊天里的“声音”“安静”误当成指令；
 * 匹配不到返回 null，照常交给大模型。</p>
 */
@Getter
final class Esp32VoiceCommand {

    /** 下发给设备的 device_command.command；为 null 表示只需安静结束本轮。 */
    private final String command;
    /** volume 命令的目标音量（0~100），其余命令为 null。 */
    private final Integer level;
    /** 指令执行后播报的确认语；为 null 表示不出声。 */
    private final String reply;

    private Esp32VoiceCommand(String command, Integer level, String reply) {
        this.command = command;
        this.level = level;
        this.reply = reply;
    }

    private static final int MAX_COMMAND_LENGTH = 12;
    private static final Pattern NOISE = Pattern.compile(
            "[\\s\\p{Punct}，。！？、；：“”‘’…—～]+|小美|小智|你好|帮我|麻烦|请|把|给我|一下|可以吗|好吗|行吗|好不好|吧|啊|呀|哦|嘛|了$");
    private static final Pattern UNMUTE = Pattern.compile("取消静音|解除静音|恢复声音|(打开|开)声音|别静音");
    private static final Pattern MUTE = Pattern.compile("^静音$|^(关掉|关闭|关)声音$");
    private static final Pattern MAX = Pattern.compile("(声音|音量)?(调到|开到|放到|调|开)?最大声?|^最大声$");
    private static final Pattern MIN = Pattern.compile("(声音|音量)?(调到|开到|放到|调|开)?最小声?|^最小声$");
    private static final Pattern DOWN = Pattern.compile(
            "(声音|音量)(再)?(调|放|开|关)?(小|低|轻)(一?点|一?些)?$|^(再)?小(一?点)?声(一?点)?$|^(再)?(小|轻)一?点$"
                    + "|^(声音)?太(吵|响)$|^你?声音太大声?$|^太大声$");
    private static final Pattern UP = Pattern.compile(
            "(声音|音量)(再)?(调|放|开)?(大|高|响)(一?点|一?些)?$|^(再)?大(一?点)?声(一?点)?$|^(再)?大一?点$"
                    + "|^(声音)?太小声?$|^(我)?听不(见|清|到)$");
    private static final Pattern STOP = Pattern.compile(
            "^(好了?|行了?)?(你?别说|不要说|别讲|不用说|别吵|停|停下|停下来|停止|安静|安静点|闭嘴|住口)$");

    static Esp32VoiceCommand match(String transcript) {
        if (transcript == null) {
            return null;
        }
        String text = NOISE.matcher(transcript).replaceAll("");
        text = NOISE.matcher(text).replaceAll("");
        if (text.isEmpty() || text.length() > MAX_COMMAND_LENGTH) {
            return null;
        }
        if (UNMUTE.matcher(text).find()) {
            return new Esp32VoiceCommand("unmute", null, "好，我回来了。");
        }
        if (MUTE.matcher(text).find()) {
            return new Esp32VoiceCommand("mute", null, null);
        }
        if (MAX.matcher(text).matches()) {
            return new Esp32VoiceCommand("volume", 100, "好，开到最大了。");
        }
        if (MIN.matcher(text).matches()) {
            return new Esp32VoiceCommand("volume", 20, "好，调到最小了。");
        }
        if (DOWN.matcher(text).find()) {
            return new Esp32VoiceCommand("volume_down", null, "好，小声点了。");
        }
        if (UP.matcher(text).find()) {
            return new Esp32VoiceCommand("volume_up", null, "好，大声点了。");
        }
        if (STOP.matcher(text).matches()) {
            return new Esp32VoiceCommand(null, null, null);
        }
        return null;
    }
}
