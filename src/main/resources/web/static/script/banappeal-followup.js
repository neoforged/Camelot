(() => {
    'use strict'

    const form = document.getElementById('followupForm')

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
        const pspl = window.location.pathname.split('/')
        xmlHttp.open("POST",  `/ban-appeals/followup/${pspl[pspl.length - 1] === '' ? pspl[pspl.length - 2] : pspl[pspl.length - 1]}`, false);
        xmlHttp.setRequestHeader("Content-Type", "application/json")
        xmlHttp.setRequestHeader("Accept", "application/json")

        xmlHttp.send(JSON.stringify({
            response: document.getElementById('response').value
        }));

        if (xmlHttp.status !== 200) {
            const responseToast = document.getElementById('responseToast')
            document.getElementById('responseToastBody').innerHTML = xmlHttp.responseText
            const toastBootstrap = bootstrap.Toast.getOrCreateInstance(responseToast)
            toastBootstrap.show()
            setTimeout(() => toastBootstrap.hide(), 5000)
        } else {
            window.location = window.location.origin + window.location.pathname + '?success&followup'
        }
    }
})()
