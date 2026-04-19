package com.example.order_service.specification;

import com.example.order_service.model.Order;
import com.example.order_service.model.OrderStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderSpecification {

    public static Specification<Order> createdAfter(Instant createdFrom) {
        return (root, query, cb) ->
                createdFrom == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
    }

    public static Specification<Order> createdBefore(Instant createdTo) {
        return (root, query, cb) ->
                createdTo == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }

    public static Specification<Order> hasStatuses(List<OrderStatus> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) return null;
            return root.get("status").in(statuses);
        };
    }

    public static Specification<Order> withFilters(
            Instant createdFrom,
            Instant createdTo,
            List<OrderStatus> statuses
    ) {
        return Specification
                .where(createdAfter(createdFrom))
                .and(createdBefore(createdTo))
                .and(hasStatuses(statuses));
    }
}