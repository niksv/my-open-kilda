import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { IslModel } from '../data-models/isl-model';
import { catchError } from 'rxjs/operators';
import { CookieManagerService } from './cookie-manager.service';

@Injectable({
  providedIn: 'root'
})
export class IslListService {
  constructor(private httpClient: HttpClient,private cookieManager: CookieManagerService) {}
  getIslList(query?:any) : Observable<IslModel[]>{
    return this.httpClient.get<IslModel[]>(`${environment.apiEndPoint}/switch/links`,{params:query});
  }
  
  getIslDetail(src_switch, src_port, dst_switch, dst_port):Observable<IslModel>{
 	let date = new Date().getTime();
    return this.httpClient.get<IslModel>(`${environment.apiEndPoint}/switch/link/props?src_switch=${src_switch}&src_port=${src_port}&dst_switch=${dst_switch}&dst_port=${dst_port}&_=${date}`);
	}
	
	getISLDetailData(src_switch, src_port, dst_switch, dst_port) : Observable<any>{
		return this.httpClient.get<any>(`${environment.apiEndPoint}/switch/links?src_switch=${src_switch}&src_port=${src_port}&dst_switch=${dst_switch}&dst_port=${dst_port}`);
	}

	islUnderMaintenance(data){
		const url = `${environment.apiEndPoint}/switch/links/under-maintenance`; 
    return this.httpClient.patch(url,data);
	}

  updateBFDflag(data){
		const url = `${environment.apiEndPoint}/switch/link/enable-bfd`; 
    return this.httpClient.patch(url,data);
	}
  updateIslBandWidth(data,src_switch,src_port,dst_switch,dst_port){
		const url = `${environment.apiEndPoint}/switch/link/bandwidth?src_switch=${src_switch}&src_port=${src_port}&dst_switch=${dst_switch}&dst_port=${dst_port}`; 
    return this.httpClient.patch(url,data);
  } 
  
	deleteIsl(data,successRes,errorRes){
     const url = `${environment.apiEndPoint}/switch/links`; 
     let token = this.cookieManager.get('XSRF-TOKEN') as string;
    var requestBody = JSON.stringify(data);
    var xhr = new XMLHttpRequest();
    xhr.withCredentials = false;
    xhr.addEventListener("readystatechange", function () {
      if (this.readyState == 4 && this.status == 200) {
        successRes(JSON.parse(this.responseText));
      }else if(this.readyState == 4 && this.status >= 300){
        errorRes(JSON.parse(this.responseText));
      }
    });
    
    xhr.open("DELETE", url);
    xhr.setRequestHeader("Content-Type", "application/json");
    if (token !== null) {
      xhr.setRequestHeader( "X-XSRF-TOKEN" , token);
    }
    xhr.send(requestBody);
	}
	
	

  updateCost(src_switch, src_port, dst_switch, dst_port, cost): Observable<{}>{
	let requestPayload = [
		{"src_switch":src_switch,
		 "src_port":src_port,
		 "dst_switch":dst_switch,
		 "dst_port":dst_port,
		 "props":{"cost":cost}
		},
		{"src_switch":dst_switch,
		 "src_port":dst_port,
		 "dst_switch":src_switch,
		 "dst_port":src_port,
		 "props":{"cost":cost}
		}
	];
        const url = `${environment.apiEndPoint}/switch/link/props`; 
        return this.httpClient.put(url,requestPayload);
  }
  
  updateDescription(src_switch, src_port, dst_switch, dst_port, description): Observable<{}>{
    let requestPayload = [
      {"src_switch":src_switch,
       "src_port":src_port,
       "dst_switch":dst_switch,
       "dst_port":dst_port,
       "props":{"description":description}
      },
      {"src_switch":dst_switch,
       "src_port":dst_port,
       "dst_switch":src_switch,
       "dst_port":src_port,
       "props":{"description":description}
      }
    ];
          const url = `${environment.apiEndPoint}/switch/link/props`; 
          return this.httpClient.put(url,requestPayload);
      }

    getLinkBFDProperties(src_switch, src_port, dst_switch, dst_port){
      let date = new Date().getTime();
      return this.httpClient.get<any>(`${environment.apiEndPoint}/switch/links/bfd?src_switch=${src_switch}&src_port=${src_port}&dst_switch=${dst_switch}&dst_port=${dst_port}&_=${date}`);
  
    }
    updateLinkBFDProperties(data,src_switch, src_port, dst_switch, dst_port){
      const url = `${environment.apiEndPoint}/switch/links/bfd?src_switch=${src_switch}&src_port=${src_port}&dst_switch=${dst_switch}&dst_port=${dst_port}`; 
      return this.httpClient.put(url,data);
    }
    deleteLinkBFDProperties(data,successRes,errorRes){
      const url = `${environment.apiEndPoint}/switch/links/bfd?src_switch=${data.src_switch}&src_port=${data.src_port}&dst_switch=${data.dst_switch}&dst_port=${data.dst_port}`; 
      let token = this.cookieManager.get('XSRF-TOKEN') as string;
     var xhr = new XMLHttpRequest();
     xhr.withCredentials = false;
     xhr.addEventListener("readystatechange", function () {
       if (this.readyState == 4 && this.status == 200) {
         successRes(JSON.parse(this.responseText));
       }else if(this.readyState == 4 && this.status >= 300){
         errorRes(JSON.parse(this.responseText));
       }
     });
     
     xhr.open("DELETE", url);
     xhr.setRequestHeader("Content-Type", "application/json");
     if (token !== null) {
       xhr.setRequestHeader( "X-XSRF-TOKEN" , token);
     }
     xhr.send();
    }
}
