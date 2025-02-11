
import { Role } from "./role";

export interface Permission {
    permission_id: number;
    name: string;
    description: string;
    status: string;
    isEditable: boolean;
    isAdminPermission: boolean;
    roles: Role;
}