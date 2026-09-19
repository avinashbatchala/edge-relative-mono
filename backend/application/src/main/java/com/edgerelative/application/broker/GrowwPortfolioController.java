package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerHolding;
import com.edgerelative.broker.api.model.BrokerMargin;
import com.edgerelative.broker.api.model.BrokerMarginOrder;
import com.edgerelative.broker.api.model.BrokerMarginRequirement;
import com.edgerelative.broker.api.model.BrokerPosition;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerUserProfile;
import com.edgerelative.broker.api.port.MarginBroker;
import com.edgerelative.broker.api.port.PortfolioBroker;
import com.edgerelative.application.broker.api.BrokerApiEnums;
import com.edgerelative.application.broker.api.RequiredMarginApiRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only Groww portfolio and margin information. The required-margin call is a pure calculation. */
@RestController
@RequestMapping("/api/v1/brokers/groww")
public class GrowwPortfolioController {

    private final PortfolioBroker portfolio;
    private final MarginBroker margin;

    public GrowwPortfolioController(PortfolioBroker portfolio, MarginBroker margin) {
        this.portfolio = portfolio;
        this.margin = margin;
    }

    @GetMapping("/portfolio/holdings")
    public List<BrokerHolding> holdings() {
        return portfolio.holdings();
    }

    @GetMapping("/portfolio/positions")
    public List<BrokerPosition> positions(@RequestParam(required = false) BrokerSegment segment) {
        return portfolio.positions(segment);
    }

    @GetMapping("/portfolio/positions/trading-symbol")
    public List<BrokerPosition> positionsForSymbol(
            @RequestParam String tradingSymbol, @RequestParam(required = false) BrokerSegment segment) {
        return portfolio.positionsForSymbol(tradingSymbol, segment);
    }

    @GetMapping("/portfolio/user")
    public BrokerUserProfile userProfile() {
        return portfolio.userProfile();
    }

    @GetMapping("/margin/user")
    public BrokerMargin userMargin() {
        return margin.userMargin();
    }

    @PostMapping("/margin/required")
    public BrokerMarginRequirement requiredMargin(@RequestBody RequiredMarginApiRequest request) {
        BrokerSegment segment = BrokerApiEnums.segment(request.segment());
        List<BrokerMarginOrder> legs = new ArrayList<>();
        if (request.orders() != null) {
            for (RequiredMarginApiRequest.OrderLeg leg : request.orders()) {
                legs.add(new BrokerMarginOrder(
                        leg.tradingSymbol(),
                        leg.quantity(),
                        leg.price(),
                        BrokerApiEnums.exchange(leg.exchange()),
                        BrokerApiEnums.segment(leg.segment()),
                        BrokerApiEnums.product(leg.product()),
                        BrokerApiEnums.orderType(leg.orderType()),
                        BrokerApiEnums.transactionType(leg.transactionType())));
            }
        }
        return margin.requiredMargin(segment, legs);
    }
}
