var checkbg = "#A7A7A7";
var nr_body = document.getElementById("read");// Phần tử body của trang đọc.
var huyandiv = document.getElementById("huyandiv");// Nút bảo vệ mắt.
var lightdiv = document.getElementById("lightdiv");// Nút bật/tắt đèn.
var fontfont = document.getElementById("fontfont");// Vùng điều khiển phông chữ.
var fontbig = document.getElementById("fontbig");// Cỡ chữ lớn.
var fontmiddle = document.getElementById("fontmiddle");// Cỡ chữ vừa.
var fontsmall = document.getElementById("fontsmall");// Cỡ chữ nhỏ.
var nr1 = document.getElementById("chaptercontent");// Nội dung chương.
// Lưu cài đặt đọc của người dùng.
function nr_setbg(intype){
    var huyandiv = document.getElementById("huyandiv");
    var light = document.getElementById("lightdiv");
    if(intype == "huyan"){
        if(huyandiv.className == "button huyanon"){
            document.cookie="light=huyan;path=/";
            set("light","huyan");
        }
        else{
            document.cookie="light=no;path=/";
            set("light","no");
        }
    }
    if(intype == "light"){
        if(light.classList.contains("lightoff")){
            document.cookie="light=yes;path=/";
            set("light","yes");
        }
        else{
            document.cookie="light=no;path=/";
            set("light","no");
        }
    }
    if(intype == "big"){
        document.cookie="font=big;path=/";
        set("font","big");
    }
    if(intype == "middle"){
        document.cookie="font=middle;path=/";
        set("font","middle");
    }
    if(intype == "small"){
        document.cookie="font=small;path=/";
        set("font","small");
    }
}

// Đọc cài đặt màu nền.
function getset(){
    var strCookie=document.cookie;
    var arrCookie=strCookie.split("; ");
    var light;
    var font;

    for(var i=0;i<arrCookie.length;i++){
        var arr=arrCookie[i].split("=");
        if("light"==arr[0]){
            light=arr[1];
            break;
        }
    }

    //light
    if(light == "yes"){
        set("light","yes");
    }
    else if(light == "no"){
        set("light","no");
    }
    else if(light == "huyan"){
        set("light","huyan");
    }
}


// Đọc cài đặt cỡ chữ.
function getset1(){
    var strCookie=document.cookie;
    var arrCookie=strCookie.split("; ");
    var light;
    var font;

    for(var j=0;j<arrCookie.length;j++){
        var arr=arrCookie[j].split("=");
        if("font"==arr[0]){
            font=arr[1];
            break;
        }
    }

    //font
    if(font == "big"){
        set("font","big");
    }
    else if(font == "middle"){
        set("font","middle");
    }
    else if(font == "small"){
        set("font","small");
    }
    else{
        set("font","middle");
    }
}

// Áp dụng cài đặt cho trang đọc.
function set(intype,p){
    if (!nr_body || !nr1) {
        return;
    }

    // Các phần tử tiêu đề/đánh dấu của giao diện cũ không còn được sử dụng.

    //var pt_prev =  document.getElementById("pt_prev1");
    //var pt_mulu =  document.getElementById("pt_mulu1");
    //var pt_next =  document.getElementById("pt_next1");
    //var pb_prev =  document.getElementById("pb_prev1");
    //var pb_mulu =  document.getElementById("pb_mulu1");
    //var pb_next =  document.getElementById("pb_next1");


    // Màu nền đọc.
    if(intype == "light"){
        if(p == "yes"){
            // Chuyển sang nền tối.
            lightdiv.innerHTML = novelMessage('lightOn', 'Bật đèn');
            lightdiv.className="button lighton";
            nr_body.style.backgroundColor = "#000";
            //nr_title.style.color = "#ccc";
            nr1.style.color = "#999";

            huyandiv.innerHTML = novelMessage('eyeCare', 'Bảo vệ mắt');
            huyandiv.className="button huyanon";
            //pt_prev.style.cssText = "background-color:#222;color:#0065B5;";
            //pt_mulu.style.cssText = "background-color:#222;color:#0065B5;";
            //pt_next.style.cssText = "background-color:#222;color:#0065B5;";
            //pb_prev.style.cssText = "background-color:#222;color:#0065B5;";
            //pb_mulu.style.cssText = "background-color:#222;color:#0065B5;";
            //pb_next.style.cssText = "background-color:#222;color:#0065B5;";
            //shuqian_2.style.color = "#999";
        }
        else if(p == "no"){
            // Chuyển sang nền sáng.
            lightdiv.innerHTML = novelMessage('lightOff', 'Tắt đèn');
            lightdiv.className="button lightoff";
            nr_body.style.backgroundColor = "#fff";
            nr1.style.color = "#000";
            //nr_title.style.color = "#000";
            //pt_prev.style.cssText = "";
            //pt_mulu.style.cssText = "";
            //pt_next.style.cssText = "";
            //pb_prev.style.cssText = "";
            //pb_mulu.style.cssText = "";
            //pb_next.style.cssText = "";
            //shuqian_2.style.color = "#000";

            huyandiv.innerHTML = novelMessage('eyeCare', 'Bảo vệ mắt');
            huyandiv.className="button huyanon";
        }
        else if(p == "huyan"){
            // Chuyển sang nền bảo vệ mắt.
            lightdiv.innerHTML = novelMessage('lightOff', 'Tắt đèn');
            lightdiv.className="button lightoff";
            huyandiv.className="button huyanoff";
            nr_body.style.backgroundColor = "#005716";
            nr1.style.color = "#000";
            //pt_prev.style.cssText = "background-color:#0E7A18;color:#000;";
            //pt_mulu.style.cssText = "background-color:#0E7A18;color:#000;";
            //pt_next.style.cssText = "background-color:#0E7A18;color:#000;";
            //pb_prev.style.cssText = "background-color:#0E7A18;color:#000;";
            //pb_mulu.style.cssText = "background-color:#0E7A18;color:#000;";
            //pb_next.style.cssText = "background-color:#0E7A18;color:#000;";
        }
    }
    // Cỡ chữ.
    if(intype == "font"){
        fontsmall.className="sizebg";
        if(p == "big"){
            fontbig.className="button sizebgon";
            nr1.style.fontSize="25px";
            fontmiddle.className="sizebg";
            fontsmall.className="sizebg";
        }
        if(p == "middle"){
            fontmiddle.className="button sizebgon";
            nr1.style.fontSize = "20px";
            fontbig.className="sizebg";
            fontsmall.className="sizebg";
        }
        if(p == "small"){
            fontsmall.className="button sizebgon";
            nr1.style.fontSize = "14px";
            fontbig.className="sizebg";
            fontmiddle.className="sizebg";
        }
    }
}
