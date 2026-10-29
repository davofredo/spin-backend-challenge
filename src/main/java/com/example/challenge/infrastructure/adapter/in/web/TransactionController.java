package com.example.challenge.infrastructure.adapter.in.web;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.application.model.TransactionSearchCriteria;
import com.example.challenge.application.port.in.ExecuteTransactionCommand;
import com.example.challenge.application.port.in.ExecuteTransactionUseCase;
import com.example.challenge.application.port.in.QueryTransactionsUseCase;
import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point for executing and querying transactions. It only translates between HTTP and the
 * use cases: every rule and every side effect happens in the application layer.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final int MAX_LIMIT = 100;
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String PROVIDER_UNAVAILABLE_DETAIL =
            "The provider did not confirm the transaction outcome; the transaction was recorded as failed";

    private final ExecuteTransactionUseCase executeTransaction;
    private final QueryTransactionsUseCase queryTransactions;

    /**
     * An unknown provider outcome is not a client error and not a success: the transaction exists as
     * {@code FAILED} but the client cannot know whether it was executed, so the answer is
     * {@code 502} with a problem detail instead of the stored transaction.
     */
    @PostMapping
    public ResponseEntity<Object> create(
            @Valid @RequestBody CreateTransactionRequest request,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
            @Size(max = 100) String idempotencyKey) {
        Transaction transaction = executeTransaction.execute(toCommand(request, idempotencyKey));

        if (transaction.status() == TransactionStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, PROVIDER_UNAVAILABLE_DETAIL));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }

    @GetMapping
    public TransactionPageResponse search(
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "status", required = false) TransactionStatus status,
            @RequestParam(name = "type", required = false) TransactionType type,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(MAX_LIMIT) int limit) {
        PageResult<Transaction> result = queryTransactions.search(
                new TransactionSearchCriteria(accountId, status, type, page, limit));
        return TransactionPageResponse.from(result);
    }

    private static ExecuteTransactionCommand toCommand(CreateTransactionRequest request, String idempotencyKey) {
        return new ExecuteTransactionCommand(
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.description(),
                idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey);
    }
}
