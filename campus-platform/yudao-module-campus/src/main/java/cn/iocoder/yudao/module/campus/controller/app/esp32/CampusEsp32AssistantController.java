package cn.iocoder.yudao.module.campus.controller.app.esp32;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.campus.framework.esp32.CampusEsp32AssistantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * ESP32 视听说服务状态接口。
 */
@RestController
@RequestMapping("/campus/esp32/assistant")
@RequiredArgsConstructor
public class CampusEsp32AssistantController {

    private final CampusEsp32AssistantProperties properties;

    @GetMapping("/health")
    @PermitAll
    public CommonResult<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", properties.isEnabled());
        data.put("configured", properties.isFullyConfigured());
        data.put("websocketPath", properties.getPath());
        data.put("protocolVersion", "esp32-av/1.0");
        data.put("mode", "half_duplex");
        data.put("pipeline", properties.isRealtimeProtocol()
                ? "native-realtime-audio" : "asr-vision-tts");
        data.put("model", properties.isRealtimeProtocol()
                ? properties.getRealtimeModelName() : properties.getModelName());
        data.put("modelApi", properties.isRealtimeProtocol()
                ? properties.getRealtimeModelUrl() : properties.getModelUrl());
        data.put("modelProtocol", properties.getResolvedModelProtocol());
        data.put("modelProvider", properties.getModelProvider());
        data.put("modelInput", properties.isRealtimeProtocol() ? "audio,image" : "text,image");
        data.put("modelOutput", properties.isRealtimeProtocol() ? "text,audio" : "text");
        data.put("inputAudioFormat", "pcm_s16le");
        data.put("inputAudioSampleRate", 16000);
        data.put("outputAudioFormat", "pcm_s16le");
        data.put("outputAudioSampleRate", 24000);
        data.put("transcriptionMode", properties.isRealtimeProtocol()
                ? "sidecar-async" : "blocking-before-model");
        // configured 只校验配置项，不代表此刻已成功连接上游模型。
        data.put("configurationOnly", true);
        return success(data);
    }

}
