(() => {
    'use strict'

    const form = document.getElementById('banAppealForm')

    form.addEventListener('submit', event => {
        if (form.checkValidity()) {
            submit()
        } else {
            event.stopPropagation()
        }
        event.preventDefault()

        form.classList.add('was-validated')
    }, false)

    function submit() {
        const xmlHttp = new XMLHttpRequest();
        xmlHttp.open("POST", window.location, false);
        xmlHttp.setRequestHeader("Content-Type", "application/json")
        xmlHttp.setRequestHeader("Accept", "application/json")

        xmlHttp.send(JSON.stringify({
            reason: document.getElementById('unbanReason').value,
            email: document.getElementById('replyEmail').value,
            feedback: document.getElementById('feedback').value
        }));

        if (xmlHttp.status !== 200) {
            const responseToast = document.getElementById('responseToast')
            document.getElementById('responseToastBody').innerHTML = xmlHttp.responseText
            const toastBootstrap = bootstrap.Toast.getOrCreateInstance(responseToast)
            toastBootstrap.show()
            setTimeout(() => toastBootstrap.hide(), 5000)
        } else {
            window.location = window.location.origin + window.location.pathname + '?success'
        }
    }
})()
