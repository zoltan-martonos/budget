const setupView = document.querySelector("#setup-view");
const appView = document.querySelector("#app-view");
const setupForm = document.querySelector("#setup-form");
const startDateSettingsForm = document.querySelector("#start-date-settings-form");
const startDateError = document.querySelector("#start-date-error");
const expenseForm = document.querySelector("#expense-form");
const assistantForm = document.querySelector("#assistant-form");
const assistantQuestion = document.querySelector("#assistant-question");
const assistantSubmit = document.querySelector("#assistant-submit");
const assistantAnswer = document.querySelector("#assistant-answer");
const assistantError = document.querySelector("#assistant-error");
const appError = document.querySelector("#app-error");
const formError = document.querySelector("#form-error");
const today = new Date();
const todayIso = localDateString(today);
let selectedMonth = new Date(today.getFullYear(), today.getMonth(), 1);
let dashboard;
let csrfToken;

function localDateString(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function euro(cents) {
    return new Intl.NumberFormat("de-DE", {
        style: "currency",
        currency: "EUR",
        minimumFractionDigits: 2
    }).format(cents / 100);
}

function displayDate(date, options = { day: "numeric", month: "long", year: "numeric" }) {
    return new Intl.DateTimeFormat("en-IE", options).format(new Date(`${date}T12:00:00`));
}

async function request(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const response = await fetch(path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(method !== "GET" && method !== "HEAD" && csrfToken
                ? { "X-CSRF-TOKEN": csrfToken } : {}),
            ...options.headers
        }
    });
    if (!response.ok) {
        const problem = await response.json().catch(() => null);
        throw new Error(problem?.detail || `Something went wrong (${response.status}).`);
    }
    return response.status === 204 ? null : response.json();
}

function showGlobalError(message) {
    appError.textContent = message;
    appError.hidden = !message;
}

async function loadDashboard() {
    showGlobalError("");
    const auth = await request("/api/auth/status");
    csrfToken = auth.csrfToken;
    document.querySelector("#account-email").textContent = auth.email || "";
    const month = `${selectedMonth.getFullYear()}-${String(selectedMonth.getMonth() + 1).padStart(2, "0")}`;
    dashboard = await request(`/api/budget?month=${encodeURIComponent(month)}`);
    if (dashboard.setupRequired) {
        setupView.hidden = false;
        appView.hidden = true;
        document.querySelector("#start-date").max = todayIso;
        document.querySelector("#start-date").value = todayIso;
        return;
    }
    setupView.hidden = true;
    appView.hidden = false;
    renderDashboard();
}

function renderDashboard() {
    document.querySelector("#current-balance").textContent = euro(dashboard.currentBalanceCents);
    document.querySelector("#total-spent").textContent = euro(dashboard.totalSpentCents);
    document.querySelector("#today-spent").textContent = euro(dashboard.todaySpentCents);
    document.querySelector("#start-date-caption").textContent = `Since ${displayDate(dashboard.startDate)}`;
    document.querySelector("#rule-start-date").textContent = displayDate(dashboard.startDate);
    const newStartDate = document.querySelector("#new-start-date");
    newStartDate.max = dashboard.startDate;
    newStartDate.value = dashboard.startDate;
    document.querySelector("#month-label").textContent = new Intl.DateTimeFormat("en-IE", {
        month: "short", year: "numeric"
    }).format(selectedMonth);
    document.querySelector("#next-month").disabled =
        selectedMonth.getFullYear() === today.getFullYear() && selectedMonth.getMonth() === today.getMonth();
    const dateInput = document.querySelector("#spent-on");
    dateInput.min = dashboard.startDate;
    dateInput.max = todayIso;
    dateInput.value = todayIso;
    renderActivity();
    renderWeeklySpending();
}

function renderWeeklySpending() {
    const weeklySpending = document.querySelector("#weekly-spending");
    weeklySpending.replaceChildren();
    if (!dashboard.weeklySpending.length) {
        const empty = document.createElement("p");
        empty.className = "weekly-empty";
        empty.textContent = "No weeks to show for this month.";
        weeklySpending.append(empty);
        return;
    }

    const weeksDescending = [...dashboard.weeklySpending].reverse();
    for (const week of weeksDescending) {
        const row = document.createElement("div");
        row.className = "weekly-row";
        const heading = document.createElement("div");
        heading.className = "weekly-row-heading";
        const dateRange = document.createElement("span");
        dateRange.textContent = `${displayDate(week.weekStart, { day: "numeric", month: "short" })} – ${displayDate(week.weekEnd, { day: "numeric", month: "short" })}`;
        const amount = document.createElement("strong");
        amount.textContent = `${euro(week.spentCents)} / ${euro(week.allowanceCents)}`;
        heading.append(dateRange, amount);
        const track = document.createElement("div");
        track.className = "weekly-bar-track";
        track.setAttribute("aria-label", `Spent ${euro(week.spentCents)} of ${euro(week.allowanceCents)} allowed`);
        const bar = document.createElement("div");
        bar.className = "weekly-bar";
        const progress = week.allowanceCents > 0
            ? Math.min(week.spentCents / week.allowanceCents * 100, 100)
            : 0;
        bar.style.width = `${progress}%`;
        track.append(bar);
        row.append(heading, track);
        weeklySpending.append(row);
    }
}

function renderActivity() {
    const activityList = document.querySelector("#activity-list");
    const emptyState = document.querySelector("#empty-state");
    activityList.replaceChildren();
    emptyState.hidden = dashboard.dailySummaries.length > 0;
    activityList.hidden = dashboard.dailySummaries.length === 0;

    const groups = new Map();
    for (const expense of dashboard.expenses) {
        if (!groups.has(expense.spentOn)) groups.set(expense.spentOn, []);
        groups.get(expense.spentOn).push(expense);
    }
    for (const summary of [...dashboard.dailySummaries].reverse()) {
        const date = summary.date;
        const expenses = groups.get(date) || [];
        const group = document.createElement("section");
        group.className = "day-group";
        const heading = document.createElement("div");
        heading.className = "day-heading";
        const dateLabel = document.createElement("span");
        dateLabel.textContent = date === dashboard.today ? "Today" : displayDate(date, { weekday: "short", day: "numeric", month: "short" });
        const balance = document.createElement("strong");
        balance.textContent = summary ? `Balance ${euro(summary.balanceCents)}` : "";
        heading.append(dateLabel, balance);
        group.append(heading);
        if (expenses.length) {
            for (const expense of expenses) group.append(createExpenseRow(expense));
        } else {
            const noSpending = document.createElement("div");
            noSpending.className = "quiet-day";
            noSpending.textContent = "No expenses";
            group.append(noSpending);
        }
        activityList.append(group);
    }
}

function createExpenseRow(expense) {
    const row = document.createElement("div");
    row.className = "expense-row";
    const icon = document.createElement("span");
    icon.className = "expense-dot";
    icon.textContent = "↘";
    const name = document.createElement("span");
    name.className = "expense-name";
    name.textContent = expense.description || "Expense";
    const amount = document.createElement("span");
    amount.className = "expense-amount";
    amount.textContent = `−${euro(expense.amountCents)}`;
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "delete-expense";
    remove.setAttribute("aria-label", `Delete ${expense.description || "expense"} for ${euro(expense.amountCents)}`);
    remove.textContent = "×";
    remove.addEventListener("click", async () => {
        if (!window.confirm("Delete this expense? Your balance will be recalculated.")) return;
        try {
            await request(`/api/budget/expenses/${expense.id}`, { method: "DELETE" });
            await loadDashboard();
        } catch (error) {
            showGlobalError(error.message);
        }
    });
    row.append(icon, name, amount, remove);
    return row;
}

setupForm.addEventListener("submit", async event => {
    event.preventDefault();
    try {
        await request("/api/budget/setup", {
            method: "POST",
            body: JSON.stringify({ startDate: new FormData(setupForm).get("startDate") })
        });
        await loadDashboard();
    } catch (error) {
        showGlobalError(error.message);
    }
});

document.querySelector("#edit-start-date-button").addEventListener("click", event => {
    const expanded = event.currentTarget.getAttribute("aria-expanded") === "true";
    event.currentTarget.setAttribute("aria-expanded", String(!expanded));
    startDateSettingsForm.hidden = expanded;
});

startDateSettingsForm.addEventListener("submit", async event => {
    event.preventDefault();
    startDateError.hidden = true;
    const startDate = new FormData(startDateSettingsForm).get("startDate");
    if (startDate >= dashboard.startDate) {
        startDateError.textContent = "Choose a date earlier than the current start date.";
        startDateError.hidden = false;
        return;
    }
    if (!window.confirm(`Move the budget start date to ${displayDate(startDate)}? Your balance will be recalculated.`)) return;
    try {
        await request("/api/budget/start-date", {
            method: "PUT",
            body: JSON.stringify({ startDate })
        });
        await loadDashboard();
        startDateSettingsForm.hidden = true;
        document.querySelector("#edit-start-date-button").setAttribute("aria-expanded", "false");
    } catch (error) {
        startDateError.textContent = error.message;
        startDateError.hidden = false;
    }
});

expenseForm.addEventListener("submit", async event => {
    event.preventDefault();
    formError.hidden = true;
    const form = new FormData(expenseForm);
    const amount = String(form.get("amount")).trim();
    const centsMatch = /^(?:\d+)(?:[.,]\d{1,2})?$/.exec(amount);
    if (!centsMatch) {
        formError.textContent = "Enter an amount with up to two decimal places.";
        formError.hidden = false;
        return;
    }
    const amountCents = Math.round(Number(amount.replace(",", ".")) * 100);
    if (!Number.isSafeInteger(amountCents) || amountCents <= 0) {
        formError.textContent = "Enter an expense greater than €0.00.";
        formError.hidden = false;
        return;
    }
    try {
        await request("/api/budget/expenses", {
            method: "POST",
            body: JSON.stringify({
                amountCents,
                description: form.get("description"),
                spentOn: form.get("spentOn")
            })
        });
        expenseForm.reset();
        await loadDashboard();
    } catch (error) {
        formError.textContent = error.message;
        formError.hidden = false;
    }
});

assistantQuestion.addEventListener("keydown", event => {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        assistantForm.requestSubmit();
    }
});

assistantForm.addEventListener("submit", async event => {
    event.preventDefault();
    assistantError.textContent = "";
    assistantError.hidden = true;
    assistantAnswer.textContent = "";
    assistantAnswer.hidden = false;
    assistantAnswer.textContent = "Thinking…";
    assistantQuestion.disabled = true;
    assistantSubmit.disabled = true;
    assistantSubmit.textContent = "Thinking…";

    try {
        const result = await request("/api/assistant/query", {
            method: "POST",
            body: JSON.stringify({ question: assistantQuestion.value.trim() })
        });
        assistantAnswer.textContent = result.answer;
    } catch (error) {
        assistantAnswer.textContent = "";
        assistantAnswer.hidden = true;
        assistantError.textContent = error.message || "The assistant could not answer right now.";
        assistantError.hidden = false;
    } finally {
        assistantQuestion.disabled = false;
        assistantSubmit.disabled = false;
        assistantSubmit.textContent = "Ask →";
        assistantQuestion.focus();
    }
});

document.querySelector("#previous-month").addEventListener("click", async () => {
    selectedMonth = new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() - 1, 1);
    try {
        await loadDashboard();
    } catch (error) {
        showGlobalError(error.message);
    }
});

document.querySelector("#next-month").addEventListener("click", async () => {
    if (selectedMonth.getFullYear() === today.getFullYear() && selectedMonth.getMonth() === today.getMonth()) return;
    selectedMonth = new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + 1, 1);
    try {
        await loadDashboard();
    } catch (error) {
        showGlobalError(error.message);
    }
});

document.querySelector("#logout-button").addEventListener("click", async () => {
    try {
        await request("/logout", { method: "POST" });
        window.location.replace("/login.html");
    } catch (error) {
        showGlobalError(error.message);
    }
});

loadDashboard().catch(error => showGlobalError(error.message));
