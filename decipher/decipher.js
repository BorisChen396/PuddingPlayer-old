function getSts(){return 18732};function getDecipher(s){return Jy(s);};var Iy={Ev:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c},rC:function(a){a.reverse()},EN:function(a,b){a.splice(0,b)}};Jy=function(a){a=a.split("");Iy.EN(a,3);Iy.rC(a,20);Iy.EN(a,3);Iy.rC(a,59);Iy.EN(a,2);Iy.Ev(a,4);Iy.Ev(a,41);Iy.Ev(a,41);Iy.rC(a,51);return a.join("")};
