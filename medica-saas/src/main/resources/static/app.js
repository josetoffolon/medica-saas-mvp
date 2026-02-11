(function () {
  angular
    .module("medicaLoginApp", [])
    .controller("LoginController", ["$http", "$timeout", function ($http, $timeout) {
      const vm = this;

      vm.credentials = {
        username: "",
        password: "",
      };
      vm.loading = false;
      vm.errorMessage = "";
      vm.successMessage = "";

      vm.login = function (form) {
        vm.errorMessage = "";
        vm.successMessage = "";

        if (form.$invalid) {
          vm.errorMessage = "Revisa los campos antes de continuar.";
          return;
        }

        vm.loading = true;

        $http
          .post("/api/auth/login", vm.credentials)
          .then(function (response) {
            const token = response.data && response.data.accessToken;
            if (token) {
              localStorage.setItem("medica_token", token);
            }

            vm.successMessage = "Inicio de sesión exitoso. Redirigiendo al panel...";
            $timeout(function () {
              window.location.hash = "#/dashboard";
            }, 1000);
          })
          .catch(function () {
            vm.errorMessage = "Usuario o contraseña inválidos.";
          })
          .finally(function () {
            vm.loading = false;
          });
      };
    }]);
})();
