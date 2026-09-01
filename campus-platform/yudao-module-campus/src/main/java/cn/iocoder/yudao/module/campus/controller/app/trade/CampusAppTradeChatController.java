package cn.iocoder.yudao.module.campus.controller.app.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeMessageRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeMessageSendReqVO;
import cn.iocoder.yudao.module.campus.service.trade.CampusTradeChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 校园交易会话")
@RestController
@RequestMapping("/campus/trade/chat")
@Validated
public class CampusAppTradeChatController {

    @Resource
    private CampusTradeChatService tradeChatService;

    @GetMapping("/messages")
    @Operation(summary = "获取订单交易会话")
    public CommonResult<List<CampusTradeMessageRespVO>> getMessages(@RequestParam("orderId") Long orderId) {
        return success(tradeChatService.getMessages(orderId, getLoginUserId()));
    }

    @PostMapping("/send")
    @Operation(summary = "发送订单交易消息")
    public CommonResult<CampusTradeMessageRespVO> send(@Valid @RequestBody CampusTradeMessageSendReqVO reqVO) {
        return success(tradeChatService.sendMessage(getLoginUserId(), reqVO));
    }
}
