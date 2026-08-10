(function () {
    "use strict";

    function preventDefault(event) {
        event.preventDefault();
    }

    function clearLegacySelection() {
        if (document.selection && typeof document.selection.empty === "function") {
            document.selection.empty();
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.hasAttribute("data-reader-restrict-selection")) {
            document.body.addEventListener("contextmenu", preventDefault);
            document.body.addEventListener("dragstart", preventDefault);
            document.body.addEventListener("beforecopy", preventDefault);
            document.body.addEventListener("copy", clearLegacySelection);
            document.body.addEventListener("select", clearLegacySelection);
        }

        document.addEventListener("click", function (event) {
            var control = event.target.closest("[data-reader-action]");
            if (!control) {
                return;
            }
            event.preventDefault();

            var action = control.getAttribute("data-reader-action");
            if (action === "history-back") {
                window.history.back();
            } else if (action === "set-mobile-theme") {
                nr_setbg(control.getAttribute("data-reader-value"));
            } else if (action === "add-shelf") {
                BookDetail.AddFavorites(0, 0, 1);
            } else if (action === "show-setup") {
                $(".maskBox,.setupBox").show();
            } else if (action === "support") {
                uFans.startSupportRead();
            } else if (action === "buy-chapter") {
                buyBookIndex();
            } else if (action === "close-qr") {
                $(".maskBox,.qrBox").hide();
            } else if (action === "close-setup") {
                $(".maskBox,.setupBox").hide();
            } else if (action === "close-support") {
                uFans.closeBox();
            } else if (action === "set-color") {
                BookDetail.SetBackUpColor(Number(control.getAttribute("data-reader-value")));
            } else if (action === "set-font-family") {
                BookDetail.SetReadFontFamily(Number(control.getAttribute("data-reader-value")));
            } else if (action === "set-font-size") {
                BookDetail.SetReadFont(Number(control.getAttribute("data-reader-value")));
            }
        });
    });
})();
