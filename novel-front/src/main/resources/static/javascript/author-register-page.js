(function (window, document, $) {
    'use strict';

    var form = document.getElementById('form1');
    if (!form || !$) return;
    var directionInput = document.getElementById('HidSexclass');
    var submit = document.getElementById('btnSubmit');

    function validDirection(value) {
        var direction = String(value == null ? '' : value);
        return direction === '0' || direction === '1' ? direction : '';
    }

    function selectDirection(value) {
        var direction = validDirection(value);
        var links = document.querySelectorAll('[data-author-direction]');
        for (var i = 0; i < links.length; i++) {
            links[i].classList.toggle('ahover', links[i].getAttribute('data-author-direction') === direction);
        }
        directionInput.value = direction;
        submit.disabled = !direction;
        submit.classList.toggle('btnGray', !direction);
        submit.classList.toggle('btn', Boolean(direction));
    }

    document.getElementById('authorDirectionOptions').addEventListener('click', function (event) {
        var target = event.target.closest('[data-author-direction]');
        if (!target) return;
        event.preventDefault();
        selectDirection(target.getAttribute('data-author-direction'));
    });

    form.addEventListener('submit', function (event) {
        if (!validDirection(directionInput.value) || !$(form).form('validate')) event.preventDefault();
    });

    selectDirection(directionInput.value);
})(window, document, window.jQuery);
