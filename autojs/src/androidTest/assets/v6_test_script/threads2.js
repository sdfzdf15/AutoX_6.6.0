

global.a = 0
global.b = 0

threads.start(function(){
    sleep(100)
    a = 4
});

threads.runAsync(function(){
    sleep(100)
    return 5
}).then((vv)=>{
    b = vv + 1
})