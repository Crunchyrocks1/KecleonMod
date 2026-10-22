'use strict';

const HUD='Niantic.Holoholo.Map.UI.PlayerHudGuiController';
const PROFILE='Niantic.Holoholo.UI.PlayerProfilePage';
const FRIENDS='Niantic.Holoholo.Social.UI.FriendsListHeaderCellView';
const MAX_RECORDS=48;

function method(type,name,parameters,result) {
  const value=type.tryMethod(name,parameters.length);
  if(!value||value.isStatic||value.virtualAddress.isNull()||value.returnType.name!==result
    ||value.parameters.length!==parameters.length||value.parameters.some((p,i)=>p.type.name!==parameters[i]))
    throw Error('Incompatible game tool method: '+type.fullName+'.'+name);
  return value;
}
function field(type,name,expected) {
  const value=type.tryField(name);
  if(!value||value.isStatic||value.offset<16||value.type.name!==expected)
    throw Error('Incompatible game tool field: '+type.fullName+'.'+name);
  return value;
}
function install(runtime,options,report) {
  const LABEL=(options.trainerLabel&&options.trainerLabel.trim()!==''?options.trainerLabel.trim():'KecleonBot');
  const hooks=[],records=new Map(),errors=[];
  let closed=false,names=false,operations=0;
  let labelReference=null,nameUpdates=0,failures=0;
  let hudPrimed=false;
  function enabled(feature){return !closed&&(feature==='names'?names:false)&&runtime.enabled()&&runtime.sameThread();}
  function error(feature,phase,cause){
    if(feature==='names')names=false;
    failures++;report('game_tools_error',{feature,phase,message:String(cause),farmingContinues:true});
  }
  function object(address,type){
    const value=runtime.object(address);
    if(!runtime.valid(value)||value.class.fullName!==type)throw Error('Invalid game tool controller: '+type);
    return value;
  }
  function remember(value,kind,read,write,replacement,group) {
    if(!runtime.valid(value)||!runtime.alive(value))return;
    const key=kind+':'+value.handle.toString();
    let entry=records.get(key);
    const current=read(value);
    if(current===replacement)return;
    if(!entry){
      for(const [oldKey,old] of [...records]){
        const held=runtime.object(runtime.address(old.reference));
        if(runtime.valid(held)&&runtime.alive(held))continue;
        old.reference.free();records.delete(oldKey);
      }
      if(records.size>=MAX_RECORDS)throw Error('Game tool reference limit reached');
      entry={reference:runtime.retain(value,'game-tools:'+kind),kind,read,write,original:current,replacement,group};
      records.set(key,entry);
    }else if(current!==entry.replacement)entry.original=current;
    write(value,replacement);
  }
  function release(group,restore=true){
    for(const [key,entry] of [...records]){
      if(group&&entry.group!==group)continue;
      try{
        const value=runtime.object(runtime.address(entry.reference));
        if(restore&&runtime.valid(value)&&runtime.alive(value)&&entry.read(value)===entry.replacement)
          entry.write(value,entry.original);
      }catch(cause){errors.push(String(cause));}
      try{entry.reference.free();records.delete(key);}catch(cause){errors.push(String(cause));}
    }
  }
  function listen(value,callbacks){const listener=runtime.attach(value.virtualAddress,callbacks);hooks.push(listener);}
  function run(feature,phase,work){
    if(!enabled(feature))return;
    operations++;
    try{work();}catch(cause){error(feature,phase,cause);}finally{operations--;}
  }
  function setName(controller,fields){
    if(!labelReference)labelReference=runtime.retain(runtime.string(LABEL),'game-tools:label');
    for(const name of fields){
      const text=controller.field(name).value;
      remember(text,'text',value=>runtime.stringContent(value.method('get_text',0).invoke()),
        (value,content)=>value.method('set_text',1).invoke(content===LABEL?runtime.stringObject(labelReference):runtime.string(content)),LABEL,'names');
      nameUpdates++;
    }
  }
  if(options.hideTrainerName)try{
    const text=runtime.resolve('UnityEngine.UI','UnityEngine.UI.Text');
    method(text,'get_text',[],'System.String');method(text,'set_text',['System.String'],'System.Void');
    const profiles=[
      {type:HUD,fields:['playerName','playerNameShadow'],methods:[['Initialize',[]],['SetVisible',['System.Boolean']],['coiw',['Niantic.Holoholo.PlayerService.PlayerInfo']]]},
      {type:PROFILE,fields:['playerNameText'],methods:[['Reinitialize',[]]]},
      {type:FRIENDS,fields:['playerName'],methods:[['SetI18NText',[]]]},
    ];
    const resolved=profiles.map(profile=>{
      const type=runtime.resolve('holo-game',profile.type);
      profile.fields.forEach(name=>field(type,name,'UnityEngine.UI.Text'));
      return {...profile,methods:profile.methods.map(([name,parameters])=>method(type,name,parameters,'System.Void'))};
    });
    names=true;
    for(const profile of resolved)for(const value of profile.methods)listen(value,{
      onEnter(args){this.silphNameController=enabled('names')?args[0]:null;},
      onLeave(){if(this.silphNameController)run('names','display',()=>setName(object(this.silphNameController,profile.type),profile.fields));},
    });
  }catch(cause){error('names','profile',cause);}
  report('game_tools_profile',{hideTrainerName:names,label:LABEL,referenceLimit:MAX_RECORDS});
  function primeHud(lookup=runtime.hud){
    if(hudPrimed||!enabled('names'))return;
    let hud;
    try{hud=lookup();if(!runtime.valid(hud)||!runtime.alive(hud))return;}
    catch(cause){hudPrimed=true;report('game_tools_hud_deferred',{message:String(cause),uiHooksAvailable:names});return;}
    hudPrimed=true;
    run('names','initial-hud',()=>setName(object(hud.handle,HUD),['playerName','playerNameShadow']));
    report('game_tools_hud_ready',{masked:names,label:LABEL});
  }
  function snapshot(){return {hideTrainerName:names&&!closed,nameUpdates,
    references:records.size+(labelReference?1:0),activeOperations:operations,failures};}
  function close(){
    closed=true;
    for(const listener of [...hooks])try{listener.detach();hooks.splice(hooks.indexOf(listener),1);}catch(cause){errors.push(String(cause));}
    const restore=records.size===0||runtime.sameThread();
    if(!restore)errors.push('Game tool restoration requires the Unity thread');
    release(null,restore);
    if(labelReference)try{labelReference.free();labelReference=null;}catch(cause){errors.push(String(cause));}
    return {passed:errors.length===0&&hooks.length===0&&records.size===0&&!labelReference&&operations===0,
      errors:[...errors],remainingHooks:hooks.length,remainingReferences:records.size+(labelReference?1:0),nameUpdates,failures};
  }
  return {primeHud,snapshot,close,get busy(){return operations>0;},get needsUnityCleanup(){return records.size>0;}};
}
module.exports={install,HUD,PROFILE,FRIENDS,MAX_RECORDS};
