const subscriptionStatus = document.getElementById("subscriptionStatus");
const subscriptionPeriod = document.getElementById("subscriptionPeriod");
const renewButton = document.getElementById("renewButton");

const sampleSubscription = {
  status: "ACTIVE",
  periodStart: "01 Mar",
  periodEnd: "31 Mar",
};

const updateSubscriptionUI = ({ status, periodStart, periodEnd }) => {
  subscriptionStatus.textContent = status === "ACTIVE" ? "ACTIVA" : "INACTIVA";
  subscriptionStatus.style.color = status === "ACTIVE" ? "#1aa672" : "#d14343";
  subscriptionPeriod.textContent = `${periodStart} - ${periodEnd}`;
  renewButton.textContent = status === "ACTIVE" ? "Gestionar" : "Renovar ahora";
};

renewButton.addEventListener("click", () => {
  alert("Redirigiendo al flujo de pago de suscripción...");
});

updateSubscriptionUI(sampleSubscription);
