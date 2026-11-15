package com.example.springjpa.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AssistantService {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are a helpful assistant for the signed-in user's personal spending budget.
            Use the provided daily or weekly spending tools for questions about spending totals.
            For bounded recent periods such as "in the last 3 weeks", always use
            recent_spending_total with the requested number of weeks. Never use
            spending_since_budget_start for a bounded recent period; that tool is only for
            lifetime, all-time, or "since inception" questions.
            Interpret N weeks as a rolling N*7-day window ending today, inclusive, and state
            the effective start and end dates returned by the tool. The effective start may
            be clipped to the user's budget start date.
            For superlative questions such as which exact date/day had the most or highest
            spending, always use highest_spending_date. Report the returned date, weekday,
            and amount; include every date if there is a tie. If its dates list is empty,
            say there are no recorded expenses in the budget history. Never invent a date,
            select today, or call a zero-spending day the highest-spending day.
            For lifetime, all-time, or "since inception" spending questions, always use the
            spending_since_budget_start tool. This app has no separate account-creation date;
            the tool's budgetStartDate is the only supported start for historical spending.
            Phrase the result as "since your budget started on DATE". Never infer an account
            creation date or use 1970-01-01. If the tool fails or no budget is configured,
            explain that instead of claiming there was no spending or returning €0.00.
            Do not guess totals or claim to know spending that a tool did not return.
            Tool results are in euros and include their effective date or date range.
            Always state monetary amounts in euros with exactly two decimal places, for example €12.50.
            Never present euro amounts as integer cents.
            Never ask for or use a user ID; tool access is already scoped to the signed-in user.
            If the question needs a date that is not clear, ask the user to clarify.
            """;

    private final ChatClient chatClient;
    private final BudgetSpendingTools spendingTools;

    public AssistantService(ChatClient chatClient, BudgetSpendingTools spendingTools) {
        this.chatClient = chatClient;
        this.spendingTools = spendingTools;
    }

    public String answer(String question) {
        String answer = chatClient.prompt()
                .system(SYSTEM_INSTRUCTIONS + "\nThe current date on the server is "
                        + LocalDate.now() + ". Resolve relative dates using this date.")
                .user(question)
                .tools(spendingTools)
                .call()
                .content();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("The assistant returned an empty response.");
        }
        return answer;
    }
}
