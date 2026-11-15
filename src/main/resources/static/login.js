const loginError = document.querySelector("#login-error");
const loginMessage = document.querySelector("#login-message");
const googleSignIn = document.querySelector("#google-sign-in");
const setupHelp = document.querySelector("#setup-help");

function maskEmail(email) {
    if (typeof email !== "string") return null;
    const at = email.lastIndexOf("@");
    return at > 0 && at < email.length - 1 ? `${email[0]}***${email.slice(at)}` : "<invalid>";
}

async function loadLoginState() {
    const response = await fetch("/api/auth/status");
    if (!response.ok) throw new Error("Could not check sign-in status.");
    const status = await response.json();
    console.info("[auth] status loaded", {
        googleConfigured: status.googleConfigured,
        authenticated: status.authenticated,
        account: maskEmail(status.email)
    });

    if (status.authenticated) {
        window.location.replace("/");
        return;
    }
    const error = new URLSearchParams(window.location.search).get("error");
    if (error) {
        console.warn("[auth] sign-in returned to login page", { category: error });
        loginError.textContent = error === "unauthorized_account"
            ? "Sign in with a Google account that has a verified email, then try again."
            : error === "oidc_configuration"
                ? "Google sign-in could not verify Google's identity token because the app's signing-key configuration is missing. Restart the updated app and try again."
            : "Google sign-in could not be completed. Check the application logs for the OAuth or callback failure category, then try again.";
        loginError.hidden = false;
    }
    if (new URLSearchParams(window.location.search).has("setup") || !status.googleConfigured) {
        setupHelp.hidden = false;
        return;
    }
    googleSignIn.hidden = false;
}

loadLoginState().catch(error => {
    console.error("[auth] could not load sign-in status", { name: error.name, message: error.message });
    loginMessage.textContent = error.message;
    loginMessage.hidden = false;
});
