function onLoad() {
    const list = document.getElementById('verify');
    list.onclick = (e) => {
        const xmlHttp = new XMLHttpRequest()
        xmlHttp.open("POST", window.location, false)
        xmlHttp.setRequestHeader("Accept", "application/json")
        xmlHttp.send()

        if (xmlHttp.status === 200) {
            window.location = window.location.origin + window.location.pathname + '?success'
        }
    }
}
