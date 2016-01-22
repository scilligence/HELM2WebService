/**
 * 
 */
function generateImageMonomer(){
	clean();
	var monomer = document.getElementById('MonomerID').value;
	var polymer = document.getElementById('PolymerType').value;
	var baseUrl = './service/';
	var inputdata = {monomerId : monomer , polymerType : polymer, showRgroups : $("#type").val()};
	$.post(baseUrl + 'Image/Monomer', inputdata).done(function(data){
		$("#outputcontainer").append('<img id="ItemPreview" src="" />');
    	document.getElementById("ItemPreview").src = data;
	}).fail(function(j){
		alert('Incorrect Input');
	});
}




function clean(){
	$("#ItemPreview").remove();
}


function resetAll(){
	document.getElementById('MonomerID').value = '';
	document.getElementById('PolymerType').value = '';
	clean();
}

window.onload = function(){
	document.getElementById("monomerImage").onclick = generateImageMonomer;
	document.getElementById("reset").onclick= resetAll;
}